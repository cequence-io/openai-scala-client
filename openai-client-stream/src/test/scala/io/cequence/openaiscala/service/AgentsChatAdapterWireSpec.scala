package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaEngineOverloadedException
}
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
  ChatRole,
  FileContent,
  FunctionCallSpec,
  ImageURLContent,
  JsonSchema,
  MessageSpec,
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
  // the stream the next session create answers with, instead of a recorded one
  @volatile private var nextSessionStream: Option[String] = None

  private def sseEvents(sse: String): Seq[String] =
    sse.split("\n\n").toSeq.filter(_.trim.nonEmpty)
  private def sseData(event: String): JsValue =
    Json.parse(event.split("\n").find(_.startsWith("data:")).get.drop(5))
  private def sseEvent(json: JsObject): String =
    s"event: ${(json \ "type").as[String]}\ndata: ${Json.stringify(json)}"
  private def sse(events: Seq[String]): String = events.mkString("", "\n\n", "\n\n")

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
              val stream = nextSessionStream.getOrElse(if (withTools) toolPause else plainTurn)
              nextSessionStream = None
              respond(201, "text/event-stream", stream)
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

  // a session is deleted asynchronously once its stream completed - every test waits for its
  // own, so no cleanup lands in the next test's requests
  private def awaitDeleted(
    sessionId: String,
    since: Int
  ): Unit =
    eventually(requests.drop(since).exists(r => r._1 == "DELETE" && r._2.endsWith(sessionId)))

  // the requests about one session since a test's start
  private def sessionRequests(
    sessionId: String,
    since: Int
  ) = requests.drop(since).filter(_._2.startsWith(s"/agents/sessions/$sessionId"))

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

      awaitDeleted(sessionId, since = 0)
    }

    "fold a history into one labeled message, images kept" in {
      val before = requests.size
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
      awaitDeleted(sessionIdOf(plainTurn), before)
    }
  }

  "a MessageSpec history" should {

    "be folded by role - developer ones as the instructions" in {
      val before = requests.size
      await(
        adapter.createChatCompletion(
          Seq(
            MessageSpec(ChatRole.Developer, "Be brief."),
            MessageSpec(ChatRole.User, "When is the meeting?"),
            MessageSpec(ChatRole.Assistant, "At 3pm"),
            UserMessage("Move it to 4")
          ),
          CreateChatCompletionSettings("gpt-6-luna")
        )
      )
      val create = requests.drop(before).find(_._2 == "/agents/sessions").get._3.get
      (create \ "agent" \ "instructions").as[String] shouldBe "Be brief."
      (create \ "input" \ 0 \ "content")
        .as[Seq[JsObject]]
        .map(c => (c \ "text").as[String]) shouldBe
        Seq("User: When is the meeting?", "Assistant: At 3pm", "User: Move it to 4")
      awaitDeleted(sessionIdOf(plainTurn), before)
    }
  }

  "structured output" should {

    "send a json_schema as the agent's text format, its objects closed" in {
      val before = requests.size
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
      awaitDeleted(sessionIdOf(plainTurn), before)
    }

    "close every object whatever strict says - the map form too" in {
      def sentSchema(schemaDef: JsonSchemaDef): JsValue = {
        val before = requests.size
        await(
          adapter.createChatCompletion(
            Seq(UserMessage("Capital of Norway?")),
            CreateChatCompletionSettings(
              "gpt-6-luna",
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(schemaDef)
            )
          )
        )
        awaitDeleted(sessionIdOf(plainTurn), before)
        val create = requests.drop(before).find(_._2 == "/agents/sessions").get._3.get
        (create \ "agent" \ "text" \ "format" \ "schema").as[JsValue]
      }

      val open = sentSchema(
        JsonSchemaDef(
          name = "capital",
          strict = false,
          structure = Left(
            JsonSchema.Object(
              properties = Seq("capital" -> JsonSchema.String()),
              required = Seq("capital"),
              additionalProperties = Some(true)
            )
          )
        )
      )
      (open \ "additionalProperties").as[Boolean] shouldBe false

      val mapForm = sentSchema(
        JsonSchemaDef(
          name = "capital",
          strict = true,
          structure = Right(
            Map(
              "type" -> "object",
              "properties" -> Map("capital" -> Map("type" -> "string")),
              "required" -> Seq("capital")
            )
          )
        )
      )
      (mapForm \ "additionalProperties").as[Boolean] shouldBe false
      (mapForm \ "properties" \ "capital" \ "type").as[String] shouldBe "string"
    }
  }

  "the session events" should {

    "turn a reasoning summary replayed without deltas into thinking" in {
      val events = sseEvents(plainTurn)
      val turnId = events.map(sseData).flatMap(e => (e \ "turn_id").asOpt[String]).head
      val summaryDone = sseEvent(
        Json.obj(
          "type" -> "agent.session.turn.reasoning_summary_text.done",
          "event_id" -> "evt_rs",
          "session_id" -> sessionIdOf(plainTurn),
          "turn_id" -> turnId,
          "item_id" -> "rs_1",
          "output_index" -> 0,
          "summary_index" -> 0,
          "text" -> "Thinking it over."
        )
      )
      nextSessionStream = Some(sse(events.take(2) ++ Seq(summaryDone) ++ events.drop(2)))

      val before = requests.size
      val chunks = await(
        adapter
          .createChatCompletionStreamedTyped(
            Seq(UserMessage("Say hi in two words.")),
            CreateChatCompletionSettings("gpt-6-luna")
          )
          .runWith(Sink.seq)
      )
      chunks.collect { case ChatChunk.Thinking(t) => t } shouldBe Seq("Thinking it over.")
      chunks.collect { case ChatChunk.Text(t) => t }.mkString shouldBe "Hi there!"
      awaitDeleted(sessionIdOf(plainTurn), before)
    }

    "fail on an error event classified by its code - a transient one retryable" in {
      val error = sseEvent(
        Json.obj(
          "type" -> "error",
          "event_id" -> "evt_err",
          "session_id" -> sessionIdOf(plainTurn),
          "error" -> Json.obj("message" -> "Overloaded", "code" -> "server_overloaded")
        )
      )
      nextSessionStream = Some(sse(sseEvents(plainTurn).take(1) :+ error))

      val before = requests.size
      intercept[OpenAIScalaEngineOverloadedException](
        await(
          adapter.createChatCompletion(
            Seq(UserMessage("Say hi.")),
            CreateChatCompletionSettings("gpt-6-luna")
          )
        )
      ).getMessage should include("Overloaded")
      // the failed turn's session is cancelled, then deleted
      awaitDeleted(sessionIdOf(plainTurn), before)
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
      sessionRequests(toolSessionId, before) shouldBe empty

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

      val resumeRequests = sessionRequests(toolSessionId, before)
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
      awaitDeleted(toolSessionId, before)
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
      val sessionId = sessionIdOf(plainTurn)
      awaitDeleted(sessionId, before)
      val cleanup = sessionRequests(sessionId, before)
      cleanup.map(r => (r._1, r._2.split("/").last)) shouldBe
        Seq(("POST", "events"), ("DELETE", sessionId))
      (cleanup.head._3.get \ "events").as[Seq[JsObject]] shouldBe
        Seq(Json.obj("type" -> "agent.session.input.cancel"))
    }

    "register a pause before its Finish goes out - a resume right after one finds it" in {
      val before = requests.size
      val (callId, _) = pendingCall
      await(
        adapter
          .createChatToolCompletionStreamed(
            weatherQuestion,
            Seq(weather),
            None,
            CreateChatCompletionSettings("gpt-6-luna")
          )
          .takeWhile(chunk => !chunk.isInstanceOf[ChatChunk.Finish], inclusive = true)
          .runWith(Sink.seq)
      ).last shouldBe a[ChatChunk.Finish]

      // resumed at once: the paused session, not a new one
      await(
        adapter.createChatToolCompletion(
          weatherQuestion ++ Seq(
            AssistantToolMessage(tool_calls =
              Seq((callId, FunctionCallSpec("get_weather", """{"city":"Paris"}""")))
            ),
            ToolMessage(Some("Sunny, 22 C"), callId, "get_weather")
          ),
          Seq(weather),
          settings = CreateChatCompletionSettings("gpt-6-luna")
        )
      ).choices.head.message.content shouldBe Some("It’s sunny in Paris, 22°C.")
      requests.drop(before).count(_._2 == "/agents/sessions") shouldBe 1
      awaitDeleted(toolSessionId, before)
    }

    "close only after the DELETE of a turn whose consumer stopped at its Finish" in {
      val ownService = OpenAIServiceFactory.withStreaming.customEngineInstance(
        engine,
        coreUrl,
        WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
      )
      val ownAdapter = ownService.agentsAsChatCompletion()
      val before = requests.size
      await(
        ownAdapter
          .createChatCompletionStreamedTyped(
            Seq(UserMessage("Say hi in two words.")),
            CreateChatCompletionSettings("gpt-6-luna")
          )
          .takeWhile(chunk => !chunk.isInstanceOf[ChatChunk.Finish], inclusive = true)
          .runWith(Sink.seq)
      ).last shouldBe a[ChatChunk.Finish]

      ownAdapter.close()
      // no waiting: close() returns only after the session's DELETE
      requests
        .drop(before)
        .exists(r => r._1 == "DELETE" && r._2.endsWith(sessionIdOf(plainTurn))) shouldBe true
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
      // nothing posted, nothing deleted
      sessionRequests(toolSessionId, before).map(_._1) shouldBe Seq("GET")

      // retried right away: the same session is resumed, not a new one started
      await(resume()).choices.head.message.content shouldBe Some("It’s sunny in Paris, 22°C.")
      requests.drop(before).exists(_._2 == "/agents/sessions") shouldBe false

      // the failed attempt never posts its results later (after its grace period)
      awaitDeleted(toolSessionId, before)
      Thread.sleep(math.max(0L, failedAt + 3500 - System.currentTimeMillis()))
      sessionRequests(toolSessionId, before).count(_._1 == "POST") shouldBe 1
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
            .createChatToolCompletion(weatherQuestion, Seq(weather), settings = settings),
        () =>
          adapter.createChatCompletion(
            Seq(
              UserSeqMessage(
                Seq(
                  TextContent("Summarize the attached"),
                  FileContent(fileId = Some("file-1"))
                )
              )
            ),
            settings
          )
      ).foreach(call => intercept[OpenAIScalaClientException](await(call())))
      requests.size shouldBe before
    }
  }
}
