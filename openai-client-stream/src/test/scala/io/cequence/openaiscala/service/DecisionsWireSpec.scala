package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaTokenCountExceededException
}
import io.cequence.openaiscala.domain.decisions._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/** `createDecision` against a local server: the path, the body and the answers read back. */
class DecisionsWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  @volatile private var reply: (Int, String) = (200, "{}")
  @volatile private var received: Option[(String, String)] = None // path, body

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val service = OpenAIServiceFactory.customEngineInstance(
    engine,
    s"http://localhost:${server.getAddress.getPort}/v1/",
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          received = Some(exchange.getRequestURI.getPath -> body)
          val (status, responseBody) = reply
          val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.getResponseHeaders.add("x-request-id", "req_wire_1")
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    engine.close()
    server.stop(0)
  }

  private val questions = Seq(
    DecisionQuestion.Predicate("Does the customer ask for a refund?", Some("refund")),
    DecisionQuestion.Choice(
      "Which team?",
      Seq(DecisionChoice("billing"), DecisionChoice("sales")),
      Some("team")
    )
  )

  "createDecision" should {

    "post the input and the questions to /decisions and read the answers" in {
      reply = 200 ->
        """{"model":"gpt-6-luna","answers":[{"type":"predicate","name":"refund","probability":0.97},{"type":"choice","name":"team","choice":"billing","probabilities":[{"value":"billing","probability":0.9},{"value":"sales","probability":0.1}],"confidence":0.8}],"usage":{"input_tokens":120,"input_tokens_details":{"cached_tokens":0,"cache_write_tokens":0},"output_tokens":0,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":120}}"""

      val decision = Await.result(
        service.createDecision(DecisionInput.Text("Refund me!"), questions),
        20.seconds
      )

      val (path, body) = received.get
      path shouldBe "/v1/decisions"
      Json.parse(body) shouldBe JsonFormats.createDecisionBody(
        DecisionInput.Text("Refund me!"),
        questions,
        CreateDecisionSettings()
      )
      decision.answer("refund") shouldBe Some(DecisionAnswer.Predicate(Some("refund"), 0.97))
      decision.usage.map(_.inputTokens) shouldBe Some(120)
      // the request id is the `x-request-id` header, not in the body
      decision.requestId shouldBe Some("req_wire_1")
    }

    "fail with OpenAI's error" in {
      reply = 400 ->
        """{"error":{"message":"Question names must be unique within the request.","type":"invalid_request_error","param":"questions[1].name","code":null}}"""

      val failure = Await.result(
        service.createDecision(DecisionInput.Text("x"), questions ++ questions).failed,
        20.seconds
      )

      failure shouldBe an[OpenAIScalaClientException]
      failure.getMessage should include("Question names must be unique")
    }

    "classify the token limit like the chat completions' (live 2026-10-07: 1.2M input tokens)" in {
      reply = 400 ->
        """{"error":{"message":"Decision input exceeds the token limit.","type":"invalid_request_error","param":"input","code":null}}"""

      Await.result(
        service.createDecision(DecisionInput.Text("x" * 100), questions).failed,
        20.seconds
      ) shouldBe an[OpenAIScalaTokenCountExceededException]
    }
  }
}
