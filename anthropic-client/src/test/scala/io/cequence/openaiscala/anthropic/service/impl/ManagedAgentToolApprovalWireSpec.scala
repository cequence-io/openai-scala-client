package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.domain.WsRequestContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CopyOnWriteArrayList, Executors, LinkedBlockingQueue, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Human approval on Anthropic Managed Agents through the typed stream of the OpenAI adapter,
 * against a local server scripting the session API with the event shapes recorded live
 * (2026-09-29, an `agent_toolset_20260401` with `always_ask`): `agent.tool_use` with
 * `evaluated_permission = ask` + a `requires_action` idle pause the run (the session is kept),
 * and a call with the decisions posts `user.tool_confirmation`s to the SAME session and
 * streams the rest of the turn (the session is deleted once it ends).
 */
class ManagedAgentToolApprovalWireSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("managed-agent-tool-approval")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(20, Millis))

  private val sessionId = "sesn_T"

  // what the session stream emits after the events a POST sent - set per test
  @volatile private var script: Seq[JsObject] => Seq[JsObject] = (_ => Nil)
  @volatile private var history: Seq[JsObject] = Nil
  @volatile private var posted: List[Seq[JsObject]] = Nil
  @volatile private var sessionsCreated = 0
  @volatile private var deleted: List[String] = Nil
  // like the live API, a POST's events reach only the streams subscribed at that moment
  private val subscribers = new CopyOnWriteArrayList[LinkedBlockingQueue[Seq[JsObject]]]()
  @volatile private var postedWithoutSubscriber = false
  @volatile private var failConfirmations = false
  @volatile private var failHistory = false

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"

  private lazy val service: OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.managedAgentAsOpenAI(
      AnthropicServiceFactory.customInstance(
        coreUrl,
        WsRequestContext(authHeaders = Seq("x-api-key" -> "k"))
      ),
      agentId = Some("agent_1"),
      environmentId = Some("env_1"),
      agentTools = Nil,
      deleteSessionsAfterUse = true
    )

  private val sessionJson =
    s"""{"id":"$sessionId","type":"session","status":"idle","environment_id":"env_1",
       |"agent":{"type":"agent","id":"agent_1","name":"a","version":1,
       |"model":{"id":"claude-sonnet-4-6","speed":"standard"}},
       |"created_at":"2026-09-29T00:00:00Z"}""".stripMargin

  private def respond(
    exchange: HttpExchange,
    status: Int,
    body: String
  ): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
    exchange.close()
  }

  override protected def beforeAll(): Unit = {
    server.setExecutor(Executors.newCachedThreadPool())
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val path = exchange.getRequestURI.getPath
          val method = exchange.getRequestMethod
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)

          (method, path) match {
            case ("POST", "/sessions") =>
              sessionsCreated += 1
              respond(exchange, 200, sessionJson)

            case ("GET", p) if p.endsWith("/events/stream") =>
              exchange.getResponseHeaders.add("Content-Type", "text/event-stream")
              exchange.sendResponseHeaders(200, 0)
              val out = exchange.getResponseBody
              out.write(": connected.\n\n".getBytes(StandardCharsets.UTF_8))
              out.flush()
              val queue = new LinkedBlockingQueue[Seq[JsObject]]()
              subscribers.add(queue)
              try
                Option(queue.poll(10, TimeUnit.SECONDS)).getOrElse(Nil).foreach { event =>
                  out.write(
                    s"data: ${Json.stringify(event)}\n\n".getBytes(StandardCharsets.UTF_8)
                  )
                  out.flush()
                }
              finally subscribers.remove(queue)
              exchange.close()

            case ("POST", p) if p.endsWith("/events") =>
              val events = (Json.parse(body) \ "events").as[Seq[JsObject]]
              posted = posted :+ events
              val confirmation =
                (events.head \ "type").asOpt[String].contains("user.tool_confirmation")
              if (failConfirmations && confirmation) {
                // the adapter aborts its event stream when the POST fails, so - as on the live
                // API - that stream is no longer subscribed; drop it at once (its handler would
                // otherwise sit registered for the full poll and swallow the next POST's events)
                subscribers.forEach(_.put(Nil))
                subscribers.clear()
                respond(exchange, 500, """{"error":{"type":"api_error","message":"boom"}}""")
              } else {
                // the SSE GET goes out first; give it a moment to register
                val deadline = System.currentTimeMillis() + 2000
                while (subscribers.isEmpty && System.currentTimeMillis() < deadline)
                  Thread.sleep(10)
                if (subscribers.isEmpty) postedWithoutSubscriber = true
                subscribers.forEach(_.put(script(events)))
                respond(exchange, 200, Json.stringify(Json.obj("data" -> events)))
              }

            case ("GET", p) if p.endsWith("/events") =>
              if (failHistory)
                respond(exchange, 500, """{"error":{"type":"api_error","message":"boom"}}""")
              else
                respond(exchange, 200, Json.stringify(Json.obj("data" -> history)))

            case ("DELETE", p) =>
              deleted = deleted :+ p.stripPrefix("/sessions/")
              respond(exchange, 200, s"""{"id":"$sessionId","type":"session_deleted"}""")

            case _ => respond(exchange, 404, """{"error":{"message":"no route"}}""")
          }
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private def reset(): Unit = {
    posted = Nil
    deleted = Nil
    sessionsCreated = 0
    history = Nil
    postedWithoutSubscriber = false
    failConfirmations = false
    failHistory = false
  }

  // -- event shapes as recorded live --

  private def toolUse(
    id: String,
    command: String,
    permission: String = "ask"
  ) = Json.obj(
    "type" -> "agent.tool_use",
    "id" -> id,
    "name" -> "bash",
    "input" -> Json.obj("command" -> command),
    "evaluated_permission" -> permission,
    "evaluation" -> Json.obj("type" -> "always_ask"),
    "processed_at" -> "2026-09-29T07:29:25Z"
  )

  private def spanEnd(output: Int) = Json.obj(
    "type" -> "span.model_request_end",
    "id" -> s"sevt_span$output",
    "is_error" -> false,
    "model_usage" -> Json.obj(
      "input_tokens" -> 3,
      "output_tokens" -> output,
      "cache_creation_input_tokens" -> 100,
      "cache_read_input_tokens" -> 10
    )
  )

  private def idleRequiresAction(ids: String*) = Json.obj(
    "type" -> "session.status_idle",
    "id" -> "sevt_idle1",
    "stop_reason" -> Json.obj("type" -> "requires_action", "event_ids" -> ids)
  )

  private val idleEndTurn = Json.obj(
    "type" -> "session.status_idle",
    "id" -> "sevt_idle2",
    "stop_reason" -> Json.obj("type" -> "end_turn")
  )

  private def toolResult(
    toolUseId: String,
    text: String,
    isError: Boolean = false
  ) = Json.obj(
    "type" -> "agent.tool_result",
    "id" -> s"sevt_res_$toolUseId",
    "tool_use_id" -> toolUseId,
    "is_error" -> isError,
    "content" -> Json.arr(Json.obj("type" -> "text", "text" -> text))
  )

  private def agentMessage(text: String) = Json.obj(
    "type" -> "agent.message",
    "id" -> "sevt_msg",
    "content" -> Json.arr(Json.obj("type" -> "text", "text" -> text))
  )

  // a user.message pauses on one bash call; a confirmation finishes the turn
  private val pauseThenFinish: Seq[JsObject] => Seq[JsObject] = events =>
    (events.head \ "type").as[String] match {
      case "user.message" =>
        Seq(toolUse("sevt_ask", "echo hi"), spanEnd(56), idleRequiresAction("sevt_ask"))
      case "user.tool_confirmation" =>
        val allowed = (events.head \ "result").as[String] == "allow"
        Seq(
          toolResult("sevt_ask", if (allowed) "hi\n" else "rejected", isError = !allowed),
          agentMessage(if (allowed) "The output is hi." else "It was denied."),
          spanEnd(14),
          idleEndTurn
        )
    }

  private val messages = Seq(UserMessage("Run echo hi with bash."))
  private val settings = CreateChatCompletionSettings("claude-sonnet-4-6")

  private def paused(): (Seq[ChatChunk], ChatChunk.ToolApprovalRequest) = {
    val chunks =
      await(service.createChatCompletionStreamedTyped(messages, settings).runWith(Sink.seq))
    val request = chunks.collectFirst { case r: ChatChunk.ToolApprovalRequest => r }.get
    (chunks, request)
  }

  "the managed-agent typed stream" should {

    "pause on a call awaiting confirmation and keep the session" in {
      reset(); script = pauseThenFinish
      val (chunks, request) = paused()

      chunks.head shouldBe ChatChunk.Start(sessionId, "claude-sonnet-4-6")
      request.requestId shouldBe "sevt_ask"
      request.toolName shouldBe "bash"
      Json.parse(request.arguments) shouldBe Json.obj("command" -> "echo hi")
      request.runId shouldBe sessionId
      request.serverName shouldBe None
      // the pending call is not on the tool layer yet
      chunks.collect { case c: ChatChunk.ToolCall => c } shouldBe empty
      chunks.collect { case f: ChatChunk.Finish => f } shouldBe
        Seq(ChatChunk.Finish(FinishReason.approval_required, Some("requires_action")))
      chunks.collectFirst { case u: ChatChunk.Usage => u.usage.completion_tokens } shouldBe
        Some(Some(56))

      (posted.head.head \ "type").as[String] shouldBe "user.message"
      postedWithoutSubscriber shouldBe false
      sessionsCreated shouldBe 1
      deleted shouldBe Nil
    }

    "resume the same session with the confirmation and stream the rest of the turn" in {
      reset(); script = pauseThenFinish
      val (_, request) = paused()
      posted = Nil

      val chunks = await(
        service
          .createChatCompletionStreamedTyped(
            Nil,
            settings.setToolApprovalDecisions(Seq(request.approve))
          )
          .runWith(Sink.seq)
      )

      sessionsCreated shouldBe 1 // no new session
      posted.head shouldBe Seq(
        Json.obj(
          "type" -> "user.tool_confirmation",
          "tool_use_id" -> "sevt_ask",
          "result" -> "allow"
        )
      )

      chunks.head shouldBe ChatChunk.Start(sessionId, "claude-sonnet-4-6")
      chunks.collect { case c: ChatChunk.ToolCall =>
        (c.callId, c.toolName, c.serverSide)
      } shouldBe
        Seq(("sevt_ask", "bash", true))
      val result = chunks.collectFirst { case r: ChatChunk.ToolResult => r }.get
      result.callId shouldBe "sevt_ask"
      result.toolName shouldBe "bash"
      result.text shouldBe Some("hi\n")
      result.isError shouldBe false
      chunks.collect { case t: ChatChunk.Text => t.text } shouldBe Seq("The output is hi.")
      chunks.collect { case f: ChatChunk.Finish => f } shouldBe
        Seq(ChatChunk.Finish(FinishReason.stop, Some("end_turn")))

      // the finished turn cleaned up BEFORE the stream completed (no race with close())
      deleted shouldBe Seq(sessionId)
    }

    "send a denial with its message" in {
      reset(); script = pauseThenFinish
      val (_, request) = paused()
      posted = Nil

      val chunks = await(
        service
          .createChatCompletionStreamedTyped(
            Nil,
            settings.setToolApprovalDecisions(Seq(request.deny("not allowed")))
          )
          .runWith(Sink.seq)
      )

      posted.head shouldBe Seq(
        Json.obj(
          "type" -> "user.tool_confirmation",
          "tool_use_id" -> "sevt_ask",
          "result" -> "deny",
          "deny_message" -> "not allowed"
        )
      )
      chunks.collectFirst { case r: ChatChunk.ToolResult => r.isError } shouldBe Some(true)
    }

    "join the pause and the resumed turn into one stream when a callback decides" in {
      reset(); script = pauseThenFinish
      val asked = new CopyOnWriteArrayList[ChatChunk.ToolApprovalRequest]()

      val chunks = await(
        service
          .createChatToolCompletionStreamedWithApprovals(messages, settings = settings) {
            request =>
              asked.add(request)
              Future.successful(request.deny("not allowed"))
          }
          .runWith(Sink.seq)
      )

      asked.size shouldBe 1
      asked.get(0).requestId shouldBe "sevt_ask"
      sessionsCreated shouldBe 1
      posted.map(events => (events.head \ "type").as[String]) shouldBe
        Seq("user.message", "user.tool_confirmation")
      (posted(1).head \ "deny_message").as[String] shouldBe "not allowed"

      chunks.head shouldBe ChatChunk.Start(sessionId, "claude-sonnet-4-6")
      chunks.collect { case s: ChatChunk.Start => s } should have size 1
      chunks.collect { case r: ChatChunk.ToolApprovalRequest => r } shouldBe empty
      chunks.collect { case c: ChatChunk.ToolCall => (c.index, c.callId) } shouldBe
        Seq((0, "sevt_ask"))
      chunks.collectFirst { case r: ChatChunk.ToolResult => r.isError } shouldBe Some(true)
      chunks.collect { case f: ChatChunk.Finish => f } shouldBe
        Seq(ChatChunk.Finish(FinishReason.stop, Some("end_turn")))
      // the usage of both rounds (56 + 14 output tokens), last
      chunks.last match {
        case ChatChunk.Usage(usage) => usage.completion_tokens shouldBe Some(70)
        case other                  => fail(s"expected the summed usage last, got $other")
      }
      deleted shouldBe Seq(sessionId)
    }

    "stream through the interim idle while the session applies several confirmations" in {
      reset()
      val askA = toolUse("sevt_A", "echo alpha")
      val askB = toolUse("sevt_B", "echo beta")
      def confirmation(toolUseId: String) = Json.obj(
        "type" -> "user.tool_confirmation",
        "id" -> s"sevt_conf_$toolUseId",
        "tool_use_id" -> toolUseId,
        "result" -> "allow"
      )
      // as recorded live: the confirmations of one POST are applied one at a time, with a
      // requires_action idle listing the still-queued one in between
      script = events =>
        (events.head \ "type").as[String] match {
          case "user.message" =>
            Seq(askA, askB, spanEnd(20), idleRequiresAction("sevt_A", "sevt_B"))
          case "user.tool_confirmation" =>
            Seq(
              confirmation("sevt_A"),
              idleRequiresAction("sevt_B"),
              confirmation("sevt_B"),
              toolResult("sevt_A", "alpha"),
              toolResult("sevt_B", "beta"),
              agentMessage("alpha and beta"),
              spanEnd(9),
              idleEndTurn
            )
        }

      val asked = new CopyOnWriteArrayList[ChatChunk.ToolApprovalRequest]()
      val assembled = await(
        service
          .createChatToolCompletionStreamedWithApprovals(messages, settings = settings) {
            request =>
              asked.add(request)
              Future.successful(request.approve)
          }
          .assembled
      )

      // each call asked about once, both confirmations in one POST
      asked.size shouldBe 2
      (asked.get(0).requestId, asked.get(1).requestId) shouldBe (("sevt_A", "sevt_B"))
      posted.map(_.map(e => (e \ "tool_use_id").asOpt[String])) shouldBe
        Seq(Seq(None), Seq(Some("sevt_A"), Some("sevt_B")))

      assembled.finishReason shouldBe Some(FinishReason.stop)
      assembled.toolResults.map(r => (r.callId, r.text)) shouldBe
        Seq(("sevt_A", Some("alpha")), ("sevt_B", Some("beta")))
      assembled.text shouldBe "alpha and beta"
      assembled.other.map(_.kind) should contain("session.status_idle")
      deleted shouldBe Seq(sessionId)
    }

    "report a call that runs without confirmation on the tool layer right away" in {
      reset()
      script = _ =>
        Seq(
          toolUse("sevt_ok", "ls", permission = "allow"),
          toolResult("sevt_ok", "a.txt"),
          agentMessage("done"),
          idleEndTurn
        )

      val assembled =
        await(service.createChatCompletionStreamedTyped(messages, settings).assembled)

      assembled.toolCalls.map(c => (c.callId, c.toolName)) shouldBe Seq(("sevt_ok", "bash"))
      assembled.toolResults.map(_.text) shouldBe Seq(Some("a.txt"))
      assembled.awaitingApproval shouldBe false
      assembled.finishReason shouldBe Some(FinishReason.stop)
    }

    "look up a pending call not seen in this stream in the session history" in {
      reset()
      history = Seq(toolUse("sevt_old", "rm -rf /tmp/x"))
      script = _ => Seq(idleRequiresAction("sevt_old"))

      val assembled =
        await(service.createChatCompletionStreamedTyped(messages, settings).assembled)

      assembled.toolApprovalRequests.map(r => (r.requestId, r.arguments)) shouldBe
        Seq(("sevt_old", """{"command":"rm -rf /tmp/x"}"""))
      assembled.finishReason shouldBe Some(FinishReason.approval_required)
    }

    "fail on a custom-tool action it cannot answer" in {
      reset()
      history = Seq(Json.obj("type" -> "agent.custom_tool_use", "id" -> "sevt_custom"))
      script = _ => Seq(idleRequiresAction("sevt_custom"))

      intercept[OpenAIScalaClientException](
        await(
          service.createChatCompletionStreamedTyped(messages, settings).runWith(Sink.ignore)
        )
      ).getMessage should include("agent.custom_tool_use")
      // an unanswerable pause is not kept - the session is cleaned up (after the failure)
      eventually(deleted shouldBe Seq(sessionId))
    }

    "stay paused on the calls a partial resume left unanswered" in {
      reset()
      val askA = toolUse("sevt_A", "echo a")
      val askB = toolUse("sevt_B", "echo b")
      history = Seq(askA, askB)
      script = events =>
        (events.head \ "type").as[String] match {
          case "user.message" =>
            Seq(askA, askB, spanEnd(10), idleRequiresAction("sevt_A", "sevt_B"))
          case "user.tool_confirmation" =>
            val answered = events.map(e => (e \ "tool_use_id").as[String]).toSet
            if (answered == Set("sevt_A"))
              // the server re-emits the idle with the remainder, no model call in between
              Seq(toolResult("sevt_A", "a"), idleRequiresAction("sevt_B"))
            else Seq(toolResult("sevt_B", "b"), agentMessage("done"), idleEndTurn)
        }

      val paused =
        await(service.createChatCompletionStreamedTyped(messages, settings).assembled)
      paused.toolApprovalRequests.map(_.requestId) shouldBe Seq("sevt_A", "sevt_B")

      val first = paused.toolApprovalRequests.head
      val partial = await(
        service
          .createChatCompletionStreamedTyped(
            Nil,
            settings.setToolApprovalDecisions(Seq(first.approve))
          )
          .assembled
      )
      // sevt_B was not seen in this stream - it is looked up in the session history
      partial.toolApprovalRequests.map(_.requestId) shouldBe Seq("sevt_B")
      partial.finishReason shouldBe Some(FinishReason.approval_required)
      deleted shouldBe Nil

      val done = await(
        service
          .createChatCompletionStreamedTyped(
            Nil,
            settings.setToolApprovalDecisions(partial.approveAll)
          )
          .assembled
      )
      done.finishReason shouldBe Some(FinishReason.stop)
      done.toolResults.map(_.callId) shouldBe Seq("sevt_B")
      deleted shouldBe Seq(sessionId)
    }

    "keep a resumed session whose next pause could not be looked up, and retry it" in {
      reset()
      val askA = toolUse("sevt_A", "echo a")
      val askB = toolUse("sevt_B", "echo b")
      history = Seq(askA, askB)
      script = events =>
        (events.head \ "type").as[String] match {
          case "user.message" =>
            Seq(askA, askB, spanEnd(10), idleRequiresAction("sevt_A", "sevt_B"))
          case "user.tool_confirmation" =>
            val answered = events.map(e => (e \ "tool_use_id").as[String]).toSet
            if (answered == Set("sevt_A"))
              Seq(toolResult("sevt_A", "a"), idleRequiresAction("sevt_B"))
            else Seq(toolResult("sevt_B", "b"), agentMessage("done"), idleEndTurn)
        }

      val first =
        await(service.createChatCompletionStreamedTyped(messages, settings).assembled)
      val decisions =
        settings.setToolApprovalDecisions(Seq(first.toolApprovalRequests.head.approve))

      // sevt_B is looked up in the history - which fails
      failHistory = true
      intercept[Exception](
        await(service.createChatCompletionStreamedTyped(Nil, decisions).runWith(Sink.ignore))
      )
      Thread.sleep(200)
      deleted shouldBe Nil

      failHistory = false
      val paused = await(service.createChatCompletionStreamedTyped(Nil, decisions).assembled)
      paused.toolApprovalRequests.map(_.requestId) shouldBe Seq("sevt_B")
      deleted shouldBe Nil

      await(
        service
          .createChatCompletionStreamedTyped(
            Nil,
            settings.setToolApprovalDecisions(paused.approveAll)
          )
          .assembled
      ).finishReason shouldBe Some(FinishReason.stop)
      deleted shouldBe Seq(sessionId)
    }

    "keep the paused session when the resume fails before the turn finishes" in {
      reset(); script = pauseThenFinish
      val (_, request) = paused()
      failConfirmations = true

      intercept[Exception](
        await(
          service
            .createChatCompletionStreamedTyped(
              Nil,
              settings.setToolApprovalDecisions(Seq(request.approve))
            )
            .runWith(Sink.ignore)
        )
      )
      Thread.sleep(200)
      deleted shouldBe Nil // the decisions can be retried

      failConfirmations = false
      await(
        service
          .createChatCompletionStreamedTyped(
            Nil,
            settings.setToolApprovalDecisions(Seq(request.approve))
          )
          .assembled
      ).finishReason shouldBe Some(FinishReason.stop)
      deleted shouldBe Seq(sessionId)
    }

    "ride out a session.error the platform retries, and classify a terminal one" in {
      reset()
      val retrying = Json.obj(
        "type" -> "session.error",
        "id" -> "sevt_err",
        "error" -> Json.obj(
          "type" -> "model_overloaded_error",
          "message" -> "Overloaded",
          "retry_status" -> Json.obj("type" -> "retrying")
        )
      )
      script = _ => Seq(retrying, agentMessage("recovered"), idleEndTurn)

      val recovered =
        await(service.createChatCompletionStreamedTyped(messages, settings).assembled)
      recovered.text shouldBe "recovered"
      recovered.other.map(_.kind) should contain("session.error")
      recovered.finishReason shouldBe Some(FinishReason.stop)

      reset()
      script = _ =>
        Seq(
          retrying ++ Json.obj(
            "error" -> Json.obj(
              "type" -> "model_overloaded_error",
              "message" -> "Overloaded",
              "retry_status" -> Json.obj("type" -> "terminal")
            )
          )
        )
      intercept[io.cequence.openaiscala.OpenAIScalaEngineOverloadedException](
        await(
          service.createChatCompletionStreamedTyped(messages, settings).runWith(Sink.ignore)
        )
      )
      // a failed stream deletes its session after the failure is delivered - let it land
      // before the next scenario resets the recorder
      eventually(deleted shouldBe Seq(sessionId))

      // `exhausted`: the retries ran out and the turn is dead (the session idles with
      // retries_exhausted) - a classified failure on every path, not a quiet finish
      def exhausted(errorType: String) = Json.obj(
        "type" -> "session.error",
        "id" -> "sevt_err2",
        "error" -> Json.obj(
          "type" -> errorType,
          "message" -> "Gave up",
          "retry_status" -> Json.obj("type" -> "exhausted")
        )
      )
      val idleExhausted = Json.obj(
        "type" -> "session.status_idle",
        "id" -> "sevt_idle3",
        "stop_reason" -> Json.obj("type" -> "retries_exhausted")
      )

      reset()
      script = _ =>
        Seq(
          agentMessage("partial"),
          retrying,
          exhausted("model_overloaded_error"),
          idleExhausted
        )
      intercept[io.cequence.openaiscala.OpenAIScalaEngineOverloadedException](
        await(
          service.createChatCompletionStreamedTyped(messages, settings).runWith(Sink.ignore)
        )
      )
      eventually(deleted shouldBe Seq(sessionId))

      reset()
      script =
        _ => Seq(agentMessage("partial"), exhausted("model_rate_limited_error"), idleExhausted)
      intercept[io.cequence.openaiscala.OpenAIScalaRateLimitException](
        await(service.createChatCompletion(messages, settings))
      ).getMessage should include("Gave up")
      intercept[io.cequence.openaiscala.OpenAIScalaRateLimitException](
        await(service.createChatCompletionStreamed(messages, settings).runWith(Sink.ignore))
      )
      eventually(deleted shouldBe Seq(sessionId, sessionId))
    }

    "fail - and delete the session - when the event stream closes before the turn ended" in {
      reset(); script = pauseThenFinish
      val (_, request) = paused()
      // the confirmation is applied, then the stream closes mid-turn
      script = events =>
        if ((events.head \ "type").as[String] == "user.tool_confirmation")
          Seq(toolResult("sevt_ask", "hi\n"))
        else pauseThenFinish(events)

      intercept[io.cequence.openaiscala.OpenAIScalaServerErrorException](
        await(
          service
            .createChatCompletionStreamedTyped(
              Nil,
              settings.setToolApprovalDecisions(Seq(request.approve))
            )
            .runWith(Sink.ignore)
        )
      ).getMessage should include("closed before the turn ended")
      // the confirmation was applied - retrying it cannot work, so the session is not kept
      eventually(deleted shouldBe Seq(sessionId))

      reset()
      script = _ => Seq(agentMessage("half"))
      intercept[io.cequence.openaiscala.OpenAIScalaServerErrorException](
        await(service.createChatCompletion(messages, settings))
      )
    }

    "refuse decisions it cannot send" in {
      reset(); script = pauseThenFinish
      val (_, request) = paused()

      val openAIRequest = request.copy(raw = Json.obj("type" -> "mcp_approval_request"))
      intercept[OpenAIScalaClientException](
        await(
          service
            .createChatCompletionStreamedTyped(
              Nil,
              settings.setToolApprovalDecisions(Seq(openAIRequest.approve))
            )
            .runWith(Sink.ignore)
        )
      ).getMessage should include("not a managed-agent tool confirmation request")

      val otherSession = request.copy(requestId = "sevt_2", runId = "sesn_other")
      intercept[OpenAIScalaClientException](
        await(
          service
            .createChatCompletionStreamedTyped(
              Nil,
              settings.setToolApprovalDecisions(Seq(request.approve, otherSession.approve))
            )
            .runWith(Sink.ignore)
        )
      ).getMessage should include("one paused session")

      // the non-typed paths cannot answer - they point at the typed stream
      intercept[OpenAIScalaClientException](
        await(
          service.createChatCompletion(
            messages,
            settings.setToolApprovalDecisions(Seq(request.approve))
          )
        )
      ).getMessage should include("typed stream")
    }
  }
}
