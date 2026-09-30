package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  MultiAgentConfig,
  ReasoningConfig,
  ReasoningContext,
  ReasoningMode
}
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  ReasoningEffort,
  ServiceTier
}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{BaseMessage, JsonSchema, ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

/**
 * What the chat completions API cannot serve goes to the Responses API: the Ultrafast service
 * tier, a reasoning mode (`setResponsesReasoningMode`) and GPT-6.1 Sol's function tools -
 * against a local server replaying Responses API answers recorded live (2026-09-29,
 * `gpt-6-astra` on the Ultrafast tier, `gpt-6.1-sol` in pro mode).
 */
class ResponsesOnlySettingsWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("responses-only-settings-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private def resource(name: String): String = {
    val source = Source.fromResource(s"responses-only/$name")
    try source.mkString
    finally source.close()
  }

  private val ultrafastJson = resource("astra-ultrafast.json")
  private val ultrafastSse = resource("astra-ultrafast.sse")
  private val proJson = resource("sol61-pro.json")
  private val proSse = resource("sol61-pro.sse")
  private val multiAgentJson = resource("sol61-multi-agent.json")
  private val multiAgentSse = resource("sol61-multi-agent.sse")

  private val chatBody =
    """{"id":"chatcmpl_1","object":"chat.completion","created":1700000000,"model":"gpt-6-astra",
      |"choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
      |"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""".stripMargin

  private val chatSse =
    """data: {"id":"chatcmpl_1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-6-astra","choices":[{"index":0,"delta":{"role":"assistant","content":"hi"},"finish_reason":null}]}
      |
      |data: {"id":"chatcmpl_1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-6-astra","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
      |
      |data: [DONE]
      |
      |""".stripMargin

  // every request received, in order
  @volatile private var requests: Vector[(String, JsObject)] = Vector.empty
  // the OpenAI-Beta headers of each request, in the order sent
  @volatile private var betaHeaders: Vector[Seq[String]] = Vector.empty

  private def last: (String, JsObject) = requests.lastOption.getOrElse(fail("no request"))
  private def lastBetaHeaders: Seq[String] = betaHeaders.lastOption.getOrElse(Nil)

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val fullStreamed =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  private lazy val full = OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
  private lazy val chatOnly =
    OpenAIChatCompletionServiceFactory.withEngine(engine, coreUrl, context)
  private lazy val chatOnlyStreamed =
    OpenAIChatCompletionServiceFactory.withStreaming.withEngine(engine, coreUrl, context)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = Json
            .parse(new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8))
            .as[JsObject]
          val path = exchange.getRequestURI.getPath
          val beta = Option(exchange.getRequestHeaders.get("OpenAI-Beta"))
            .map(values => values.toArray(Array.empty[String]).toSeq)
            .getOrElse(Nil)
          synchronized {
            requests = requests :+ (path -> body)
            betaHeaders = betaHeaders :+ beta
          }

          val streamed = (body \ "stream").asOpt[Boolean].contains(true)
          val pro = (body \ "reasoning" \ "mode").asOpt[String].contains("pro")
          val multiAgent = (body \ "multi_agent").isDefined

          val (contentType, response) =
            if (!path.endsWith("/responses"))
              if (streamed) ("text/event-stream", chatSse) else ("application/json", chatBody)
            else if (streamed)
              (
                "text/event-stream",
                if (multiAgent) multiAgentSse else if (pro) proSse else ultrafastSse
              )
            else
              (
                "application/json",
                if (multiAgent) multiAgentJson else if (pro) proJson else ultrafastJson
              )

          exchange.getResponseHeaders.add("Content-Type", contentType)
          val bytes = response.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    fullStreamed.close()
    full.close()
    chatOnly.close()
    chatOnlyStreamed.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private val messages: Seq[BaseMessage] = Seq(UserMessage("Say hi in two words."))

  private val weather = FunctionTool(
    name = "get_weather",
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  private val ultrafast =
    CreateChatCompletionSettings(
      ModelId.gpt_6_astra,
      service_tier = Some(ServiceTier.ultrafast)
    )

  private val pro = CreateChatCompletionSettings(
    ModelId.gpt_6_1_sol,
    reasoning_effort = Some(ReasoningEffort.none)
  ).setResponsesReasoningMode(ReasoningMode.pro)

  "the Ultrafast service tier" should {

    "go through the Responses API on the full service - sync, tools and both streams" in {
      val sync = await(full.createChatCompletion(messages, ultrafast))
      last._1 should endWith("/responses")
      (last._2 \ "service_tier").as[String] shouldBe "ultrafast"
      sync.contentHead shouldBe "Hi there!"

      await(full.createChatToolCompletion(messages, Seq(weather), None, ultrafast))
      last._1 should endWith("/responses")
      (last._2 \ "service_tier").as[String] shouldBe "ultrafast"

      val chunks =
        await(fullStreamed.createChatCompletionStreamed(messages, ultrafast).runWith(Sink.seq))
      last._1 should endWith("/responses")
      (last._2 \ "service_tier").as[String] shouldBe "ultrafast"
      (last._2 \ "stream").as[Boolean] shouldBe true
      chunks.flatMap(_.choices.headOption.flatMap(_.delta.content)).mkString shouldBe
        "Hi there!"

      val typed =
        await(fullStreamed.createChatCompletionStreamedTyped(messages, ultrafast).assembled)
      last._1 should endWith("/responses")
      typed.text shouldBe "Hi there!"
    }

    "be left to the API by a chat-only service" in {
      await(chatOnly.createChatCompletion(messages, ultrafast)).contentHead shouldBe "hi"
      last._1 should endWith("/chat/completions")
      (last._2 \ "service_tier").as[String] shouldBe "ultrafast"

      await(
        chatOnlyStreamed.createChatCompletionStreamed(messages, ultrafast).runWith(Sink.seq)
      )
      last._1 should endWith("/chat/completions")
      (last._2 \ "service_tier").as[String] shouldBe "ultrafast"
    }

    "not reroute the other tiers" in {
      Seq(ServiceTier.priority, ServiceTier.fast, ServiceTier.flex).foreach { tier =>
        withClue(tier) {
          await(full.createChatCompletion(messages, ultrafast.copy(service_tier = Some(tier))))
          last._1 should endWith("/chat/completions")
          (last._2 \ "service_tier").as[String] shouldBe tier.toString
        }
      }
    }
  }

  "a reasoning mode (setResponsesReasoningMode)" should {

    "go through the Responses API on the full service, with the effort converted" in {
      await(full.createChatCompletion(messages, pro)).contentHead should not be empty
      last._1 should endWith("/responses")
      // gpt-6.1-sol rejects 'none' - lifted to 'low'
      (last._2 \ "reasoning")
        .as[JsObject] shouldBe Json.obj("effort" -> "low", "mode" -> "pro")

      await(fullStreamed.createChatCompletionStreamedTyped(messages, pro).assembled)
      last._1 should endWith("/responses")
      (last._2 \ "reasoning" \ "mode").as[String] shouldBe "pro"
      (last._2 \ "reasoning" \ "summary").as[String] shouldBe "auto"

      await(fullStreamed.createChatCompletionStreamed(messages, pro).runWith(Sink.seq))
      last._1 should endWith("/responses")
      (last._2 \ "reasoning" \ "mode").as[String] shouldBe "pro"

      // a mode alone is sent too
      await(
        full.createChatCompletion(
          messages,
          CreateChatCompletionSettings(ModelId.gpt_6_sol)
            .setResponsesReasoningMode(ReasoningMode.standard)
        )
      )
      (last._2 \ "reasoning").as[JsObject] shouldBe Json.obj("mode" -> "standard")
    }

    "be refused - never dropped - where the chat completions API would serve the call" in {
      val before = requests.size
      Seq[() => Future[_]](
        () => chatOnly.createChatCompletion(messages, pro),
        () => chatOnly.createChatToolCompletion(messages, Seq(weather), None, pro),
        () =>
          chatOnlyStreamed.createChatCompletionStreamed(messages, pro).runWith(Sink.ignore),
        () =>
          chatOnlyStreamed
            .createChatCompletionStreamedTyped(messages, pro)
            .runWith(Sink.ignore),
        () => full.createChatFunCompletion(messages, Nil, None, pro)
      ).foreach { call =>
        intercept[OpenAIScalaClientException](await(call())).getMessage should
          include("setResponsesReasoningMode")
      }
      requests.size shouldBe before
    }
  }

  "GPT-6.1 Sol function tools" should {

    "go through the Responses API even with reasoning_effort 'none', and fail fast chat-only" in {
      val none = CreateChatCompletionSettings(
        ModelId.gpt_6_1_sol,
        reasoning_effort = Some(ReasoningEffort.none)
      )
      await(full.createChatToolCompletion(messages, Seq(weather), None, none))
      last._1 should endWith("/responses")
      (last._2 \ "reasoning" \ "effort").as[String] shouldBe "low"
      (last._2 \ "tools" \ 0 \ "name").as[String] shouldBe "get_weather"

      val before = requests.size
      intercept[OpenAIScalaClientException](
        await(chatOnly.createChatToolCompletion(messages, Seq(weather), None, none))
      ).getMessage should include("function tools on the chat completions API")
      intercept[OpenAIScalaClientException](
        await(
          chatOnlyStreamed
            .createChatToolCompletionStreamed(messages, Seq(weather), None, none)
            .runWith(Sink.ignore)
        )
      ).getMessage should include("function tools on the chat completions API")
      requests.size shouldBe before
    }

    "get the Astra rules on chat completions without tools" in {
      await(
        full.createChatCompletion(
          messages,
          CreateChatCompletionSettings(
            ModelId.gpt_6_1_sol,
            max_tokens = Some(100),
            temperature = Some(0.2),
            reasoning_effort = Some(ReasoningEffort.max)
          )
        )
      )
      last._1 should endWith("/chat/completions")
      (last._2 \ "reasoning_effort").as[String] shouldBe "xhigh"
      (last._2 \ "max_completion_tokens").as[Int] shouldBe 100
      (last._2 \ "max_tokens").toOption shouldBe None
      (last._2 \ "temperature").asOpt[Double].forall(_ == 1d) shouldBe true
    }
  }

  "multi-agent execution (beta)" should {
    val multiAgentSettings = CreateModelResponseSettings(
      model = ModelId.gpt_6_1_sol,
      multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2)))
    )
    val rootAnswer = "Fruits: apple, banana, orange; vegetables: carrots, broccoli, spinach."

    "send the beta header - as the only OpenAI-Beta one - with multi_agent, sync and streamed" in {
      val response =
        await(
          full.createModelResponse(Inputs.Text("Fruits and vegetables?"), multiAgentSettings)
        )
      lastBetaHeaders shouldBe Seq("responses_multi_agent=v1")
      (last._2 \ "multi_agent").as[JsObject] shouldBe
        Json.obj("enabled" -> true, "max_concurrent_subagents" -> 2)
      response.outputText shouldBe Some(rootAnswer)

      await(
        fullStreamed
          .createModelResponseStreamed(
            Inputs.Text("Fruits and vegetables?"),
            multiAgentSettings
          )
          .runWith(Sink.seq)
      ).size should be > 10
      lastBetaHeaders shouldBe Seq("responses_multi_agent=v1")
    }

    "send no OpenAI-Beta header at all without a beta feature" in {
      await(
        full.createModelResponse(Inputs.Text("hi"), multiAgentSettings.copy(multiAgent = None))
      )
      lastBetaHeaders shouldBe Nil
      await(
        full.createChatCompletion(messages, CreateChatCompletionSettings(ModelId.gpt_6_sol))
      )
      last._1 should endWith("/chat/completions")
      lastBetaHeaders shouldBe Nil
    }

    "route setResponsesMultiAgent through the Responses API, answering with the root agent" in {
      val settings = CreateChatCompletionSettings(
        ModelId.gpt_6_1_sol,
        reasoning_effort = Some(ReasoningEffort.low)
      ).setResponsesMultiAgent()

      await(full.createChatCompletion(messages, settings)).contentHead shouldBe rootAnswer
      last._1 should endWith("/responses")
      (last._2 \ "multi_agent" \ "enabled").as[Boolean] shouldBe true
      lastBetaHeaders shouldBe Seq("responses_multi_agent=v1")

      // the typed stream asks for no reasoning summary (the API rejects it with multi-agent)
      val typed =
        await(fullStreamed.createChatCompletionStreamedTyped(messages, settings).assembled)
      last._1 should endWith("/responses")
      (last._2 \ "reasoning").as[JsObject] shouldBe Json.obj("effort" -> "low")
      typed.text shouldBe "Fruits: apple, banana, orange; vegetables: carrot, broccoli, spinach."
      typed.toolCalls.map(_.toolName) shouldBe
        Seq("multi_agent.spawn_agent", "multi_agent.spawn_agent", "multi_agent.wait_agent")
      typed.toolCalls.forall(_.serverSide) shouldBe true
    }

    "be refused where the chat completions API would serve the call" in {
      val settings = CreateChatCompletionSettings(ModelId.gpt_6_1_sol).setResponsesMultiAgent()
      val before = requests.size
      Seq[() => Future[_]](
        () => chatOnly.createChatCompletion(messages, settings),
        () =>
          chatOnlyStreamed
            .createChatCompletionStreamed(messages, settings)
            .runWith(Sink.ignore)
      ).foreach { call =>
        intercept[OpenAIScalaClientException](await(call())).getMessage should
          include("setResponsesMultiAgent")
      }
      requests.size shouldBe before
    }
  }

  "the Responses API" should {

    "parse the reasoning mode / context and the tier a response reports" in {
      val ultrafastResponse = await(
        full.createModelResponse(
          Inputs.Text("Say hi in two words."),
          CreateModelResponseSettings(
            model = ModelId.gpt_6_astra,
            serviceTier = Some("ultrafast")
          )
        )
      )
      ultrafastResponse.serviceTier shouldBe Some("ultrafast")
      ultrafastResponse.reasoning shouldBe Some(
        ReasoningConfig(
          effort = Some(ReasoningEffort.medium),
          mode = Some(ReasoningMode.standard),
          context = Some(ReasoningContext.all_turns)
        )
      )

      val proResponse = await(
        full.createModelResponse(
          Inputs.Text("Say hi in two words."),
          CreateModelResponseSettings(
            model = ModelId.gpt_6_1_sol,
            reasoning = Some(
              ReasoningConfig(
                effort = Some(ReasoningEffort.low),
                mode = Some(ReasoningMode.pro)
              )
            )
          )
        )
      )
      (last._2 \ "reasoning")
        .as[JsObject] shouldBe Json.obj("effort" -> "low", "mode" -> "pro")
      proResponse.reasoning.flatMap(_.mode) shouldBe Some(ReasoningMode.pro)
    }
  }
}
