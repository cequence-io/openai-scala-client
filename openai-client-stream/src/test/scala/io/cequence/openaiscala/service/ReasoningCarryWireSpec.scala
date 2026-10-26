package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.JsonFormats.{messageReads, messageWrites}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
  FunctionCallSpec,
  JsonSchema,
  ModelId,
  ReasoningBlock,
  ReasoningMessage,
  ToolMessage,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsValue, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

/**
 * Reasoning carried between the rounds of a stateless tool loop, on the wire against a local
 * server: a GPT-6 tool completion (routed to the Responses API) asks for the encrypted
 * reasoning, the response's `historyMessages` hold it as a [[ReasoningMessage]] before the
 * assistant's tool message, and the next round sends it back before the function calls - to
 * the service that returned it only - also when the next round is a typed stream of the merged
 * streamed service. The chat completions API never sees it.
 */
class ReasoningCarryWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("reasoning-carry-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private case class Received(
    path: String,
    body: JsObject
  )

  private val received = mutable.ArrayBuffer.empty[Received]

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val full = OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
  // the merged sync + streamed service (OpenAIServiceFactory.withStreaming())
  private lazy val fullStreamed =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  // another service - e.g. another provider or account a router fails over to
  private lazy val other = OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
  private lazy val chatOnly =
    OpenAIChatCompletionServiceFactory.withEngine(
      engine,
      coreUrl = coreUrl,
      requestContext = context
    )
  // the same provider under another account
  private lazy val chatOtherAccount =
    OpenAIChatCompletionServiceFactory.withEngine(
      engine,
      coreUrl = coreUrl,
      requestContext = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k2"))
    )

  // a GPT-6 reply that reasoned (encrypted, as asked) and then called a tool
  private val responsesBody =
    """{"id":"resp_1","object":"response","created_at":1700000000,"status":"completed","model":"gpt-6-luna",
      |"output":[
      |{"type":"reasoning","id":"rs_1","summary":[{"type":"summary_text","text":"Checking Oslo."}],"encrypted_content":"enc-1"},
      |{"type":"function_call","id":"fc_1","call_id":"call_1","name":"get_weather","arguments":"{\"city\":\"Oslo\"}","status":"completed"}],
      |"parallel_tool_calls":true,"text":{"format":{"type":"text"}},
      |"usage":{"input_tokens":10,"input_tokens_details":{"cached_tokens":0},"output_tokens":20,"output_tokens_details":{"reasoning_tokens":12},"total_tokens":30}}""".stripMargin

  // a reply with a second reasoning item that came without encrypted content
  private val twoItemsBody = responsesBody.replace(
    "\"encrypted_content\":\"enc-1\"},",
    "\"encrypted_content\":\"enc-1\"},\n{\"type\":\"reasoning\",\"id\":\"rs_2\",\"summary\":[]},"
  )

  // a chat completions provider that reasons (DeepSeek, Fireworks: `reasoning_content`) and
  // calls a tool
  private val chatReasoningBody =
    """{"id":"chatcmpl_2","object":"chat.completion","created":1700000000,"model":"deepseek-reasoner",
      |"choices":[{"index":0,"message":{"role":"assistant","content":"","reasoning_content":"Closer to Oslo.",
      |"tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"Oslo\"}"}}]},
      |"finish_reason":"tool_calls"}],
      |"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""".stripMargin

  // a typed stream's answer: a Responses API stream recorded live (gpt-6-astra, 2026-09-29)
  private val responsesSse = {
    val source = Source.fromResource("responses-only/astra-ultrafast.sse")
    try source.mkString
    finally source.close()
  }

  private val chatBody =
    """{"id":"chatcmpl_1","object":"chat.completion","created":1700000000,"model":"gpt-6-luna",
      |"choices":[{"index":0,"message":{"role":"assistant","content":"4C in Oslo"},"finish_reason":"stop"}],
      |"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""".stripMargin

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val path = exchange.getRequestURI.getPath
          val json = Json.parse(body).as[JsObject]
          received.synchronized {
            received += Received(path, json)
          }

          val streamed = (json \ "stream").asOpt[Boolean].contains(true)
          val response =
            if (
              !path
                .endsWith("/responses") && (json \ "user").asOpt[String].contains("reasoner")
            )
              chatReasoningBody
            else if (!path.endsWith("/responses"))
              chatBody
            else if (streamed)
              responsesSse
            else if ((json \ "user").asOpt[String].contains("two-items"))
              twoItemsBody
            else
              responsesBody
          exchange.getResponseHeaders.add(
            "Content-Type",
            if (streamed)
              "text/event-stream"
            else
              "application/json"
          )
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
    full.close()
    fullStreamed.close()
    other.close()
    chatOnly.close()
    chatOtherAccount.close()
    engine.close()
    server.stop(0)
    system.terminate()
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private def lastRequest(): Received = received.synchronized(received.last)

  private val weather = FunctionTool(
    name = "get_weather",
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  private val question = UserMessage("Weather in Oslo?")
  private val luna = CreateChatCompletionSettings(ModelId.gpt_6_luna)

  private val carried = ReasoningMessage(
    Seq(
      ReasoningBlock(
        summary = Seq("Checking Oslo."),
        encrypted = Some("enc-1"),
        id = Some("rs_1")
      )
    )
  )

  private val toolCall = AssistantToolMessage(
    tool_calls = Seq("call_1" -> FunctionCallSpec("get_weather", """{"city":"Oslo"}"""))
  )
  private val toolResult = ToolMessage(Some("4C"), "call_1", "get_weather")

  // the type of each Responses input item (a message is "message")
  private def inputTypes(body: JsObject): Seq[String] =
    (body \ "input").as[Seq[JsObject]].map { item =>
      (item \ "type").asOpt[String].getOrElse("message")
    }

  private def reasoningItems(body: JsObject): Seq[JsObject] =
    (body \ "input")
      .as[Seq[JsObject]]
      .filter(item => (item \ "type").asOpt[String].contains("reasoning"))

  "A GPT-6 tool completion" should {

    "ask for the encrypted reasoning and return it before the tool message" in {
      val response =
        await(full.createChatToolCompletion(Seq(question), Seq(weather), None, luna))

      val request = lastRequest()
      request.path should endWith("/responses")
      (request.body \ "store").as[Boolean] shouldBe false
      (request.body \ "include").as[Seq[String]] shouldBe Seq("reasoning.encrypted_content")

      response.reasoningMessage shouldBe Some(carried)
      response.historyMessages shouldBe Seq(carried, response.assistantToolMessage)
      response.assistantToolMessage.tool_calls.map(_._1) shouldBe Seq("call_1")
    }

    "send the carried reasoning back next round, before the function call" in {
      val first = await(full.createChatToolCompletion(Seq(question), Seq(weather), None, luna))
      val history = Seq(question) ++ first.historyMessages :+ toolResult

      await(full.createChatToolCompletion(history, Seq(weather), None, luna))

      val request = lastRequest()
      inputTypes(request.body) shouldBe Seq(
        "message",
        "reasoning",
        "function_call",
        "function_call_output"
      )
      val Seq(reasoning) = reasoningItems(request.body)
      (reasoning \ "id").as[String] shouldBe "rs_1"
      (reasoning \ "encrypted_content").as[String] shouldBe "enc-1"
      (reasoning \ "summary" \ 0 \ "text").as[String] shouldBe "Checking Oslo."
    }

    "leave out an item without encrypted content unless the request is stored" in {
      val first = await(
        full.createChatToolCompletion(
          Seq(question),
          Seq(weather),
          None,
          luna.copy(user = Some("two-items"))
        )
      )
      first.reasoningMessage.map(_.blocks.flatMap(_.id)) shouldBe Some(Seq("rs_1", "rs_2"))
      val history = Seq(question) ++ first.historyMessages :+ toolResult

      await(full.createChatToolCompletion(history, Seq(weather), None, luna))
      reasoningItems(lastRequest().body).map(r => (r \ "id").as[String]) shouldBe Seq("rs_1")

      await(
        full
          .createChatToolCompletion(history, Seq(weather), None, luna.copy(store = Some(true)))
      )
      val stored = lastRequest().body
      reasoningItems(stored).map(r => (r \ "id").as[String]) shouldBe Seq("rs_1", "rs_2")
      // a stored reply keeps its reasoning server-side - no need for the encrypted copy
      (stored \ "include").toOption shouldBe None
    }

    "send back only the reasoning its own service returned" in {
      val first = await(full.createChatToolCompletion(Seq(question), Seq(weather), None, luna))
      val history = Seq(question) ++ first.historyMessages :+ toolResult

      // another provider or account answers a 400 for it
      await(other.createChatToolCompletion(history, Seq(weather), None, luna))
      inputTypes(lastRequest().body) shouldBe Seq(
        "message",
        "function_call",
        "function_call_output"
      )

      // nor one this service never returned
      val madeUp =
        ReasoningMessage(
          Seq(
            ReasoningBlock(id = Some("rs_made_up"), encrypted = Some("enc-x"))
          )
        )
      await(
        full.createChatToolCompletion(
          Seq(question, madeUp, toolCall, toolResult),
          Seq(weather),
          None,
          luna
        )
      )
      reasoningItems(lastRequest().body) shouldBe Nil

      // while the history read back from JSON still goes to the service that returned it
      val readBack =
        history.map(m => Json.toJson(m)(messageWrites).as[BaseMessage](messageReads))
      await(full.createChatToolCompletion(readBack, Seq(weather), None, luna))
      reasoningItems(lastRequest().body).map(r => (r \ "id").as[String]) shouldBe Seq("rs_1")
    }

    "carry a sync round's reasoning into a typed stream of the merged streamed service" in {
      // a fresh merged service: its registry holds only what this test's round returns
      val merged =
        OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
      try {
        val first =
          await(merged.createChatToolCompletion(Seq(question), Seq(weather), None, luna))
        first.reasoningMessage shouldBe Some(carried)

        // the wrapped service answered the sync round; the stream runs through the
        // Responses adapter over the wrapper - which must know the reasoning all the same
        val chunks = await(
          merged
            .createChatToolCompletionStreamed(
              Seq(question) ++ first.historyMessages :+ toolResult,
              Seq(weather),
              None,
              luna
            )
            .runWith(Sink.seq)
        )
        chunks should not be empty

        val request = lastRequest()
        request.path should endWith("/responses")
        (request.body \ "stream").as[Boolean] shouldBe true
        inputTypes(request.body) shouldBe Seq(
          "message",
          "reasoning",
          "function_call",
          "function_call_output"
        )
        reasoningItems(request.body).map(r => (r \ "id").as[String]) shouldBe Seq("rs_1")
      } finally merged.close()
    }

    "share the reasoning among the Responses views of the merged streamed service" in {
      val merged =
        OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
      try {
        // a reply of the Responses view over the merged service, then a sync round of the
        // merged service itself (served by the wrapped service's own Responses adapter)
        val first = await(
          merged.responsesAsChatCompletion
            .createChatToolCompletion(Seq(question), Seq(weather), None, luna)
        )
        first.reasoningMessage shouldBe Some(carried)

        await(
          merged.createChatToolCompletion(
            Seq(question) ++ first.historyMessages :+ toolResult,
            Seq(weather),
            None,
            luna
          )
        )
        reasoningItems(lastRequest().body).map(r => (r \ "id").as[String]) shouldBe Seq("rs_1")

        // while a separate service of the same account keeps its own
        val separate = OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
        try {
          await(
            separate.createChatToolCompletion(
              Seq(question) ++ first.historyMessages :+ toolResult,
              Seq(weather),
              None,
              luna
            )
          )
          reasoningItems(lastRequest().body) shouldBe Nil
        } finally separate.close()
      } finally merged.close()
    }

    "not ask a model that does not reason for encrypted reasoning" in {
      val adapter = OpenAIResponsesChatCompletionService(full)

      await(
        adapter
          .createChatCompletion(Seq(question), CreateChatCompletionSettings(ModelId.gpt_4_1))
      )

      (lastRequest().body \ "include").toOption shouldBe None
    }
  }

  "The chat completions API" should {

    "never see the carried reasoning" in {
      // e.g. a failover of the same history to a chat-only service
      await(
        chatOnly.createChatToolCompletion(
          Seq(question, carried, toolCall, toolResult),
          Seq(weather),
          None,
          luna
        )
      )

      val request = lastRequest()
      request.path should endWith("/chat/completions")
      val messages = (request.body \ "messages").as[Seq[JsValue]]
      messages.map(m => (m \ "role").as[String]) shouldBe Seq("user", "assistant", "tool")
      messages.exists(m => (m \ "reasoning_items").isDefined) shouldBe false
    }
  }

  "A chat completions reasoning model" should {

    val reasoner = CreateChatCompletionSettings("deepseek-reasoner", user = Some("reasoner"))

    def assistantMessage(body: JsObject): JsObject =
      (body \ "messages")
        .as[Seq[JsObject]]
        .find(m => (m \ "role").as[String] == "assistant")
        .get

    "return its reasoning_content as the reply's reasoning, and get it back on the next round" in {
      val first =
        await(chatOnly.createChatToolCompletion(Seq(question), Seq(weather), None, reasoner))
      first.reasoningMessage shouldBe Some(
        ReasoningMessage(
          Seq(
            ReasoningBlock(text = Some("Closer to Oslo."), field = Some("reasoning_content"))
          )
        )
      )

      val history = Seq(question) ++ first.historyMessages :+ toolResult
      await(chatOnly.createChatToolCompletion(history, Seq(weather), None, luna))

      val request = lastRequest().body
      (request \ "messages").as[Seq[JsObject]].map(m => (m \ "role").as[String]) shouldBe Seq(
        "user",
        "assistant",
        "tool"
      )
      (assistantMessage(request) \ "reasoning_content").as[String] shouldBe "Closer to Oslo."
    }

    "send it back in the field it came in, and only to the account that returned it" in {
      val first =
        await(chatOnly.createChatToolCompletion(Seq(question), Seq(weather), None, reasoner))
      val inReasoning = first.historyMessages.map {
        case ReasoningMessage(blocks) =>
          ReasoningMessage(blocks.map(_.copy(field = Some("reasoning"))))
        case other => other
      }
      // a text this account returned, whatever field it is put in (`reasoning`: Cerebras)
      await(
        chatOnly.createChatToolCompletion(
          Seq(question) ++ inReasoning :+ toolResult,
          Seq(weather),
          None,
          luna
        )
      )
      (assistantMessage(lastRequest().body) \ "reasoning")
        .as[String] shouldBe "Closer to Oslo."
      (assistantMessage(lastRequest().body) \ "reasoning_content").toOption shouldBe None

      // another account of the same provider gets none
      await(
        chatOtherAccount.createChatToolCompletion(
          Seq(question) ++ first.historyMessages :+ toolResult,
          Seq(weather),
          None,
          luna
        )
      )
      (assistantMessage(lastRequest().body) \ "reasoning_content").toOption shouldBe None

      // nor a text no one returned
      await(
        chatOnly.createChatToolCompletion(
          Seq(
            question,
            ReasoningMessage(Seq(ReasoningBlock(text = Some("Made up.")))),
            toolCall,
            toolResult
          ),
          Seq(weather),
          None,
          luna
        )
      )
      (assistantMessage(lastRequest().body) \ "reasoning_content").toOption shouldBe None
    }

    "share what it returned with the full service on the same account" in {
      val first =
        await(chatOnly.createChatToolCompletion(Seq(question), Seq(weather), None, reasoner))
      // the full service's chat completions path (gpt-4.1 stays on chat completions)
      await(
        full.createChatToolCompletion(
          Seq(question) ++ first.historyMessages :+ toolResult,
          Seq(weather),
          None,
          CreateChatCompletionSettings(ModelId.gpt_4_1)
        )
      )
      lastRequest().path should endWith("/chat/completions")
      (assistantMessage(lastRequest().body) \ "reasoning_content")
        .as[String] shouldBe "Closer to Oslo."
    }
  }

  "A history with carried reasoning" should {

    "survive a JSON round trip" in {
      val history: Seq[BaseMessage] = Seq(question, carried, toolCall, toolResult)

      val json = Json.toJson(history.map(Json.toJson(_)(messageWrites)))

      json.as[Seq[JsValue]].map(_.as[BaseMessage](messageReads)) shouldBe history
    }
  }
}
