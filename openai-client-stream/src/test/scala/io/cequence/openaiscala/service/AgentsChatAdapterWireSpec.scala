package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort,
  Verbosity
}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionTool,
  FunctionCallSpec,
  ImageURLContent,
  JsonSchema,
  SystemMessage,
  TextContent,
  ToolMessage,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.domain.responsesapi.tools.WebSearchTool
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsValue, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * The chat-completion adapter over the Agents API (`agentsAsChatCompletion`) against a local
 * server replaying sessions recorded live on 2026-09-30: a plain turn (the session deleted
 * afterwards), a client function call that pauses the session (kept) and its resume by the
 * call carrying the tool result (subscribe, post, the replayed items skipped), the session
 * lifecycle when a stream stops early, the request it builds, and the refusals.
 */
class AgentsChatAdapterWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("agents-chat-adapter-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private def resource(name: String): String = {
    val source = scala.io.Source.fromResource(s"agents/$name")
    try source.mkString
    finally source.close()
  }

  private val plainTurn = resource("basic.sse")
  private val toolPause = resource("tool-1.sse")
  private val toolResumeReplay = resource("tool-2.sse")

  private def sessionIdOf(sse: String) =
    (Json.parse(sse.split("\n").find(_.startsWith("data:")).get.drop(5)) \ "session" \ "id")
      .as[String]
  private val toolSessionId = sessionIdOf(toolPause)
  private val pendingCall = {
    val requiresAction = toolPause
      .split("\n")
      .collect { case l if l.startsWith("data:") => Json.parse(l.drop(5)) }
      .find(e => (e \ "type").as[String] == "agent.session.requires_action")
      .get
    (
      (requiresAction \ "session" \ "required_actions" \ 0 \ "call_id").as[String],
      (requiresAction \ "session" \ "required_actions" \ 0 \ "turn_id").as[String]
    )
  }

  @volatile private var requests: Vector[(String, String, Option[JsValue])] = Vector.empty
  @volatile private var failNextSubscription = false

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private lazy val service = OpenAIServiceFactory.withStreaming.customEngineInstance(
    engine,
    coreUrl,
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )
  private lazy val adapter = service.agentsAsChatCompletion()

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val bytes = exchange.getRequestBody.readAllBytes()
          val body =
            if (bytes.isEmpty) None
            else Some(Json.parse(new String(bytes, StandardCharsets.UTF_8)))
          val method = exchange.getRequestMethod
          val path = exchange.getRequestURI.getPath
          synchronized { requests = requests :+ ((method, path, body)) }

          def respond(
            status: Int,
            contentType: String,
            content: String
          ): Unit = {
            val out = content.getBytes(StandardCharsets.UTF_8)
            exchange.getResponseHeaders.add("Content-Type", contentType)
            if (out.isEmpty) exchange.sendResponseHeaders(status, -1)
            else {
              exchange.sendResponseHeaders(status, out.length.toLong)
              exchange.getResponseBody.write(out)
            }
            exchange.close()
          }

          val withTools = body.exists(b => (b \ "agent" \ "tools").isDefined)
          (method, path) match {
            case ("POST", "/agents/sessions") =>
              respond(201, "text/event-stream", if (withTools) toolPause else plainTurn)
            case ("GET", p) if p.endsWith("/events") && failNextSubscription =>
              failNextSubscription = false
              respond(
                500,
                "application/json",
                """{"error":{"message":"boom","type":"server_error"}}"""
              )
            case ("GET", p) if p.endsWith("/events") =>
              respond(200, "text/event-stream", toolResumeReplay)
            case ("POST", p) if p.endsWith("/events") => respond(202, "application/json", "")
            case ("DELETE", p) =>
              respond(
                200,
                "application/json",
                s"""{"id":"${p
                    .split("/")
                    .last}","object":"agent.session.deleted","deleted":true}"""
              )
            case _ => respond(404, "application/json", """{"error":{"message":"not found"}}""")
          }
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private def eventually(condition: => Boolean): Unit = {
    val deadline = System.currentTimeMillis() + 5000
    while (!condition && System.currentTimeMillis() < deadline) Thread.sleep(50)
    condition shouldBe true
  }

  private val weather = FunctionTool(
    name = "get_weather",
    description = Some("Get the current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )
  private val weatherQuestion: Seq[BaseMessage] =
    Seq(UserMessage("What's the weather in Paris right now?"))

  "a plain turn" should {

    "run one session, answer with the final answer, and delete the session" in {
      val chunks = await(
        adapter
          .createChatCompletionStreamedTyped(
            Seq(SystemMessage("Be brief."), UserMessage("Say hi in two words.")),
            CreateChatCompletionSettings(
              "gpt-6-luna",
              reasoning_effort = Some(ReasoningEffort.low),
              temperature = Some(0.2)
            )
          )
          .runWith(Sink.seq)
      )
      val sessionId = sessionIdOf(plainTurn)
      chunks.head shouldBe ChatChunk.Start(sessionId, "gpt-6-luna")
      chunks.collect { case ChatChunk.Text(t) => t }.mkString shouldBe "Hi there!"
      chunks.collect { case f: ChatChunk.Finish => f.reason } shouldBe Seq(FinishReason.stop)

      val create = requests.find(r => r._1 == "POST" && r._2 == "/agents/sessions").get._3.get
      (create \ "agent").as[JsObject] shouldBe Json.obj(
        "model" -> "gpt-6-luna",
        "instructions" -> "Be brief.",
        "reasoning" -> Json.obj("effort" -> "low")
      )
      // a lone user message is sent as is
      (create \ "input" \ 0 \ "content").as[Seq[JsObject]] shouldBe
        Seq(Json.obj("type" -> "input_text", "text" -> "Say hi in two words."))
      (create \ "stream").as[Boolean] shouldBe true

      eventually(requests.exists(r => r._1 == "DELETE" && r._2.endsWith(sessionId)))
    }

    "fold a history into one labeled message, images kept" in {
      await(
        adapter.createChatCompletion(
          Seq(
            UserMessage("Remember 7."),
            io.cequence.openaiscala.domain.AssistantMessage("ok"),
            UserSeqMessage(
              Seq(TextContent("What is on it?"), ImageURLContent("data:image/png;base64,AAA"))
            )
          ),
          CreateChatCompletionSettings("gpt-6-luna")
        )
      ).contentHead shouldBe "Hi there!"

      val create =
        requests.filter(r => r._1 == "POST" && r._2 == "/agents/sessions").last._3.get
      (create \ "input" \ 0 \ "content").as[Seq[JsObject]] shouldBe Seq(
        Json.obj("type" -> "input_text", "text" -> "User: Remember 7."),
        Json.obj("type" -> "input_text", "text" -> "Assistant: ok"),
        Json.obj("type" -> "input_text", "text" -> "User:"),
        Json.obj("type" -> "input_text", "text" -> "What is on it?"),
        Json.obj("type" -> "input_image", "image_url" -> "data:image/png;base64,AAA")
      )
    }
  }

  "structured output" should {

    "send a json_schema as the agent's text format, its objects closed" in {
      await(
        adapter.createChatCompletion(
          Seq(UserMessage("Capital of Norway?")),
          CreateChatCompletionSettings(
            "gpt-6-luna",
            response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
            jsonSchema = Some(
              JsonSchemaDef(
                name = "capital",
                strict = true,
                structure = Left(
                  JsonSchema.Object(
                    properties = Seq("capital" -> JsonSchema.String()),
                    required = Seq("capital")
                  )
                )
              )
            ),
            verbosity = Some(Verbosity.low)
          )
        )
      )
      val create =
        requests.filter(r => r._1 == "POST" && r._2 == "/agents/sessions").last._3.get
      (create \ "agent" \ "text").as[JsObject] shouldBe Json.obj(
        "format" -> Json.obj(
          "type" -> "json_schema",
          "schema" -> Json.obj(
            "type" -> "object",
            "properties" -> Json.obj("capital" -> Json.obj("type" -> "string")),
            "required" -> Json.arr("capital"),
            "additionalProperties" -> false
          )
        ),
        "verbosity" -> "low"
      )
    }
  }

  "a client function tool" should {

    "pause the session with the pending call, then resume it with the tool result" in {
      val before = requests.size
      val paused = await(
        adapter.createChatToolCompletion(
          weatherQuestion,
          Seq(weather),
          settings = CreateChatCompletionSettings("gpt-6-luna")
        )
      )
      val (callId, turnId) = pendingCall
      paused.id shouldBe toolSessionId
      paused.choices.head.finish_reason shouldBe Some("tool_calls")
      paused.choices.head.message.tool_calls shouldBe
        Seq((callId, FunctionCallSpec("get_weather", """{"city":"Paris"}""")))
      val create = requests.drop(before).find(_._2 == "/agents/sessions").get._3.get
      (create \ "agent" \ "tools" \ 0 \ "name").as[String] shouldBe "get_weather"
      Thread.sleep(300)
      // the paused session is kept
      requests.drop(before).exists(_._1 == "DELETE") shouldBe false

      val resumed = await(
        adapter
          .createChatToolCompletionStreamed(
            weatherQuestion ++ Seq(
              AssistantToolMessage(tool_calls = paused.choices.head.message.tool_calls),
              ToolMessage(Some("Sunny, 22 C"), callId, "get_weather")
            ),
            Seq(weather),
            None,
            CreateChatCompletionSettings("gpt-6-luna")
          )
          .runWith(Sink.seq)
      )
      // the answer (replayed as done events - no deltas), the commentary as thinking, and
      // nothing re-reported
      resumed.head shouldBe ChatChunk.Start(toolSessionId, "gpt-6-luna")
      resumed.collect { case ChatChunk.Text(t) =>
        t
      }.mkString shouldBe "It’s sunny in Paris, 22°C."
      resumed.collect { case ChatChunk.Thinking(t) => t }.mkString shouldBe
        "I’ll check the current conditions in Paris."
      resumed.collect { case c: ChatChunk.ToolCall => c } shouldBe empty
      resumed.collect { case f: ChatChunk.Finish => f.reason } shouldBe Seq(FinishReason.stop)

      val resumeRequests = requests.drop(before).dropWhile(_._1 != "GET")
      resumeRequests.map(r => (r._1, r._2.split("/").last)).take(2) shouldBe
        Seq(("GET", "events"), ("POST", "events"))
      (resumeRequests(1)._3.get \ "events").as[Seq[JsObject]] shouldBe Seq(
        Json.obj(
          "type" -> "agent.session.input.tool_result",
          "turn_id" -> turnId,
          "call_id" -> callId,
          "success" -> true,
          "output" -> "Sunny, 22 C"
        )
      )
      eventually(requests.exists(r => r._1 == "DELETE" && r._2.endsWith(toolSessionId)))
    }
  }

  "a stream that stops early" should {

    "cancel, then delete the session of a turn the consumer abandoned" in {
      val before = requests.size
      await(
        adapter
          .createChatCompletionStreamedTyped(
            Seq(UserMessage("Say hi in two words.")),
            CreateChatCompletionSettings("gpt-6-luna")
          )
          .take(1)
          .runWith(Sink.seq)
      ) shouldBe Seq(ChatChunk.Start(sessionIdOf(plainTurn), "gpt-6-luna"))

      // the turn is cancelled first (a session mid-turn cannot be deleted), then deleted
      val sessionPath = s"/agents/sessions/${sessionIdOf(plainTurn)}"
      def cleanup = requests.drop(before).filter(_._2.startsWith(sessionPath))
      eventually(
        cleanup
          .dropWhile(r => !(r._1 == "POST" && r._2 == s"$sessionPath/events"))
          .exists(_._1 == "DELETE")
      )
      val cancel = cleanup.find(_._1 == "POST").get
      (cancel._3.get \ "events").as[Seq[JsObject]] shouldBe
        Seq(Json.obj("type" -> "agent.session.input.cancel"))
    }

    "keep a session paused when its resume failed before posting the results" in {
      val paused = await(
        adapter.createChatToolCompletion(
          weatherQuestion,
          Seq(weather),
          settings = CreateChatCompletionSettings("gpt-6-luna")
        )
      )
      val (callId, _) = pendingCall
      val resumeMessages = weatherQuestion ++ Seq(
        AssistantToolMessage(tool_calls = paused.choices.head.message.tool_calls),
        ToolMessage(Some("Sunny, 22 C"), callId, "get_weather")
      )
      def resume() = adapter.createChatToolCompletion(
        resumeMessages,
        Seq(weather),
        settings = CreateChatCompletionSettings("gpt-6-luna")
      )

      val before = requests.size
      val failedAt = System.currentTimeMillis()
      failNextSubscription = true
      intercept[Exception](await(resume()))
      requests.drop(before).map(_._1) shouldBe Seq("GET") // nothing posted, nothing deleted

      // retried right away: the same session is resumed, not a new one started
      await(resume()).choices.head.message.content shouldBe Some("It’s sunny in Paris, 22°C.")
      requests.drop(before).exists(_._2 == "/agents/sessions") shouldBe false

      // the failed attempt never posts its results later (after its grace period)
      Thread.sleep(math.max(0L, failedAt + 3500 - System.currentTimeMillis()))
      requests.drop(before).count(r => r._1 == "POST" && r._2.endsWith("/events")) shouldBe 1
    }
  }

  "the refusals" should {

    "refuse what the Agents API cannot carry - before any request" in {
      val before = requests.size
      val settings = CreateChatCompletionSettings("gpt-6-luna")
      Seq[() => Future[_]](
        () =>
          adapter.createChatCompletion(
            weatherQuestion,
            settings.setResponsesTools(Seq(WebSearchTool()))
          ),
        () =>
          adapter.createChatToolCompletion(
            weatherQuestion,
            Seq(ChatCompletionTool.SkillTool("pptx")),
            settings = settings
          ),
        () =>
          adapter.createChatToolCompletion(
            weatherQuestion,
            Seq(ChatCompletionTool.MCPServerTool("x", "https://x", requireApproval = true)),
            settings = settings
          ),
        () =>
          service
            .agentsAsChatCompletion(agentId = Some("agent_1"))
            .createChatToolCompletion(weatherQuestion, Seq(weather), settings = settings)
      ).foreach(call => intercept[OpenAIScalaClientException](await(call())))
      requests.size shouldBe before
    }
  }
}
