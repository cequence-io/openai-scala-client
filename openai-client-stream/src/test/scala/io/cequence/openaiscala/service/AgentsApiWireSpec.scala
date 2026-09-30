package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.SortOrder
import io.cequence.openaiscala.domain.agents.AgentSessionEvent._
import io.cequence.openaiscala.domain.agents._
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
 * The Agents API (beta) end to end against a local server: the endpoints and bodies, the
 * `OpenAI-Beta: agents=v1` header (the only one sent), the session event streams - with the
 * comment-only heartbeat frames and CRLF framing the real engine must get through - and the
 * error classification. The streamed turn replays a session recorded live on 2026-09-30.
 */
class AgentsApiWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("agents-api-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private val recordedTurn = {
    val source = scala.io.Source.fromResource("agents/basic.sse")
    try source.mkString
    finally source.close()
  }

  // the recorded frames with a heartbeat comment frame after every event, CRLF-framed
  private val heartbeatCrlfTurn: String =
    recordedTurn
      .split("\n\n")
      .filter(_.trim.nonEmpty)
      .map(frame => frame.replace("\n", "\r\n") + "\r\n\r\n:\r\n\r\n")
      .mkString

  private final case class Recorded(
    method: String,
    path: String,
    query: Option[String],
    body: Option[JsValue],
    betas: Seq[String],
    idempotencyKey: Option[String]
  )

  @volatile private var requests: Vector[Recorded] = Vector.empty
  private def last: Recorded = requests.lastOption.getOrElse(fail("no request"))

  private val sessionJson =
    """{"id":"sess_1","object":"agent.session","created_at":1790749885,"status":"idle","required_actions":[],"error":null,"agent":{"id":"agent_1","model":"gpt-6-luna","tools":[]},"environment":{"type":"none"},"vault_ids":[],"usage":null,"metadata":{}}"""
  private val agentJson =
    """{"id":"agent_1","object":"agent","created_at":1790749690,"updated_at":1790749690,"name":"a","metadata":{},"model":"gpt-6-luna","tools":[{"type":"web_search","context_size":"low"}]}"""
  private def page(item: String) =
    s"""{"object":"list","data":[$item],"first_id":"x","last_id":"x","has_more":false}"""
  private val turnJson =
    """{"id":"turn_1","object":"agent.session.turn","session_id":"sess_1","agent_id":"agent_1","status":"completed","created_at":1,"error":null,"usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15,"input_tokens_details":{},"output_tokens_details":{}}}"""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private lazy val service = OpenAIServiceFactory.withStreaming.customEngineInstance(
    engine,
    coreUrl,
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val bytes = exchange.getRequestBody.readAllBytes()
          val body =
            if (bytes.isEmpty) None
            else Some(Json.parse(new String(bytes, StandardCharsets.UTF_8)))
          val headers = exchange.getRequestHeaders
          val path = exchange.getRequestURI.getPath
          val method = exchange.getRequestMethod
          synchronized {
            requests = requests :+ Recorded(
              method,
              path,
              Option(exchange.getRequestURI.getQuery),
              body,
              Option(headers.get("OpenAI-Beta"))
                .map(_.toArray(Array.empty[String]).toSeq)
                .getOrElse(Nil),
              Option(headers.getFirst("Idempotency-Key"))
            )
          }

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

          val streamed = body.exists(b => (b \ "stream").asOpt[Boolean].contains(true))
          (method, path) match {
            case ("POST", "/agents/sessions") if streamed =>
              respond(201, "text/event-stream", heartbeatCrlfTurn)
            case ("POST", "/agents/sessions") => respond(201, "application/json", sessionJson)
            case ("GET", "/agents/sessions/sess_1/events") =>
              respond(200, "text/event-stream", heartbeatCrlfTurn)
            case ("POST", "/agents/sessions/sess_1/events") =>
              respond(202, "application/json", "")
            case ("DELETE", "/agents/sessions/busy") =>
              respond(
                409,
                "application/json",
                """{"error":{"message":"Session has an active turn.","type":"invalid_request_error","code":"conflict"}}"""
              )
            case ("DELETE", _) =>
              respond(
                200,
                "application/json",
                """{"id":"x","object":"deleted","deleted":true}"""
              )
            case ("GET", "/agents/sessions/sess_1/artifacts/art_1/content") =>
              respond(200, "application/octet-stream", "file-bytes")
            case (_, p)
                if p
                  .startsWith("/agents/sessions/sess_1/subagents/sub_1/turns/turn_1/items") =>
              respond(
                200,
                "application/json",
                page(
                  """{"type":"message","id":"m","turn_id":"turn_1","role":"assistant","content":[{"type":"output_text","text":"hi"}],"status":"completed","phase":"final_answer"}"""
                )
              )
            case (_, p) if p.endsWith("/turns") =>
              respond(200, "application/json", page(turnJson))
            case (_, p) if p.contains("/turns/") => respond(200, "application/json", turnJson)
            case ("GET", "/agents/sessions/sess_1") =>
              respond(200, "application/json", sessionJson)
            case (_, "/agents") | (_, "/agents/agent_1")
                if method == "GET" && path == "/agents" =>
              respond(200, "application/json", page(agentJson))
            case (_, p) if p.startsWith("/agents/agent_1") || p == "/agents" =>
              respond(200, "application/json", agentJson)
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

  "the agents endpoints" should {

    "send the agents=v1 beta header - as the only OpenAI-Beta one - on every call" in {
      val agent =
        await(service.createAgent(CreateAgentSettings("gpt-6-luna", name = Some("a"))))
      agent.tools shouldBe Seq(AgentTool.WebSearch(contextSize = Some("low")))
      last shouldBe Recorded(
        "POST",
        "/agents",
        None,
        Some(Json.obj("model" -> "gpt-6-luna", "name" -> "a")),
        Seq("agents=v1"),
        None
      )

      await(
        service.listAgents(limit = Some(5), order = Some(SortOrder.desc))
      ).data should have size 1
      (last.method, last.path, last.query) shouldBe ("GET", "/agents", Some(
        "limit=5&order=desc"
      ))

      await(service.updateAgent("agent_1", UpdateAgentSettings(instructions = Some("x"))))
      (last.method, last.path, last.body) shouldBe
        ("POST", "/agents/agent_1", Some(Json.obj("instructions" -> "x")))

      await(service.deleteAgent("agent_1")).deleted shouldBe true
      (last.method, last.path, last.betas) shouldBe ("DELETE", "/agents/agent_1", Seq(
        "agents=v1"
      ))
    }

    "create, read, list and delete sessions, and page their items / turns" in {
      val created = await(
        service.createAgentSession(
          CreateAgentSessionSettings(agentId = Some("agent_1")),
          Some(AgentInput.Text("hi"))
        )
      )
      created.id shouldBe "sess_1"
      last.body shouldBe Some(
        Json.obj(
          "agent_id" -> "agent_1",
          "environment" -> Json.obj("type" -> "none"),
          "input" -> "hi"
        )
      )

      await(service.getAgentSession("sess_1")).agent.map(_.model) shouldBe Some("gpt-6-luna")

      val turns = await(service.listAgentSessionTurns("sess_1", subagentId = Some("sub_1")))
      last.path shouldBe "/agents/sessions/sess_1/subagents/sub_1/turns"
      turns.data.head.usage.map(_.totalTokens) shouldBe Some(15)

      await(service.getAgentSessionTurn("sess_1", "turn_1")).status shouldBe "completed"
      last.path shouldBe "/agents/sessions/sess_1/turns/turn_1"

      val items = await(
        service
          .listAgentSessionItems("sess_1", subagentId = Some("sub_1"), turnId = Some("turn_1"))
      )
      last.path shouldBe "/agents/sessions/sess_1/subagents/sub_1/turns/turn_1/items"
      items.data.collect { case m: AgentSessionItem.Message => m.text } shouldBe Seq("hi")

      await(service.deleteAgentSession("sess_1")).deleted shouldBe true
    }

    "post input events (202, no body) with an idempotency key" in {
      await(
        service.sendAgentSessionEvents(
          "sess_1",
          Seq(AgentSessionInput.text("next"), AgentSessionInput.Cancel),
          idempotencyKey = Some("key-1")
        )
      ) shouldBe (())
      last.idempotencyKey shouldBe Some("key-1")
      (last.body.get \ "events").as[Seq[JsObject]].map(e => (e \ "type").as[String]) shouldBe
        Seq("agent.session.input.message", "agent.session.input.cancel")
    }

    "stream an artifact's content" in {
      val bytes = await(
        service
          .getAgentSessionArtifactContent("sess_1", "art_1")
          .flatMap(_.runWith(Sink.fold(akka.util.ByteString.empty)(_ ++ _)))
      )
      bytes.utf8String shouldBe "file-bytes"
    }

    "refuse a turn's items without a subagent as a failed Future, not a synchronous throw" in {
      val call =
        scala.util.Try(service.listAgentSessionItems("sess_1", turnId = Some("turn_1")))
      call.isSuccess shouldBe true
      intercept[IllegalArgumentException](await(call.get)).getMessage should include(
        "subagent"
      )
    }

    "fail a rejected call classified (a 409 deleting a session with an active turn)" in {
      intercept[OpenAIScalaClientException](
        await(service.deleteAgentSession("busy"))
      ).getMessage should
        include("active turn")
    }
  }

  "the session event streams" should {

    "decode a created session's stream through heartbeat frames and CRLF framing" in {
      val events = await(
        service
          .createAgentSessionStreamed(
            CreateAgentSessionSettings(
              agent = Some(AgentConfig(model = Some("gpt-6-luna")))
            ),
            AgentInput.Text("Say hi in two words.")
          )
          .runWith(Sink.seq)
      )
      last.body.flatMap(b => (b \ "stream").asOpt[Boolean]) shouldBe Some(true)
      last.betas shouldBe Seq("agents=v1")

      events.head.eventType shouldBe SessionCreated
      events.last.eventType shouldBe SessionIdle
      events.collect { case d: OutputTextDelta => d.delta }.mkString shouldBe "Hi there!"
      AgentSessionEvents.finalAnswer(events) shouldBe "Hi there!"
    }

    "subscribe to a session's events and end the subscription when the turn settles" in {
      val events = await(
        service
          .streamAgentSessionEvents("sess_1")
          .via(AgentSessionEvents.untilSettled())
          .runWith(Sink.seq)
      )
      (last.method, last.path, last.betas) shouldBe
        ("GET", "/agents/sessions/sess_1/events", Seq("agents=v1"))
      events.last.eventType shouldBe SessionIdle
      AgentSessionEvents.finalAnswer(events) shouldBe "Hi there!"
    }
  }
}
