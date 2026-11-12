package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{
  AnthropicScalaRateLimitException,
  AnthropicServiceFactory,
  AnthropicServiceZIO
}
import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentEvent, ClaudeAgentSettings}
import io.cequence.openaiscala.claudeagent.service.{
  ClaudeAgentServiceFactory,
  ClaudeAgentServiceZIO
}
import io.cequence.openaiscala.testkit.TestEngines
import io.cequence.openaiscala.typesafe.domain.{
  ModelMetadata,
  NoulQuestion,
  Question,
  SystemOneResponse
}
import io.cequence.openaiscala.typesafe.service.{
  TypeSafeService,
  TypeSafeServiceFactory,
  TypeSafeServiceZIO
}
import io.cequence.wsclient.domain.WsRequestContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsValue
import zio.{durationInt, Exit, Promise, Runtime, Unsafe, ZIO, ZLayer}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

/**
 * The providers' generated ZIO views: an HTTP stream (Anthropic) and a plain call (a decision
 * service) against a local server, and the Claude agent's hot event stream over a fake CLI -
 * lazy Tasks, cold ZStreams, the providers' own classified exceptions, a layer that closes.
 */
class ProviderServicesZIOWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  private val messageRequests = new AtomicInteger(0)
  private val decisionRequests = new AtomicInteger(0)
  @volatile private var rateLimited = false

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val plainEngine = TestEngines.plain()
  private lazy val baseUrl = s"http://localhost:${server.getAddress.getPort}"

  private lazy val anthropic = AnthropicServiceZIO(
    AnthropicServiceFactory.customInstance(
      s"$baseUrl/",
      WsRequestContext(authHeaders = Seq("x-api-key" -> "k"))
    )
  )

  private lazy val decisions = TypeSafeServiceZIO(
    TypeSafeServiceFactory.withEngine(
      plainEngine,
      apiKey = "test-key",
      baseUrl = baseUrl,
      defaultModel = "jev-latest"
    )
  )

  private val messageStream =
    "event: message_start\ndata: " +
      """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":5,"output_tokens":1}}}""" +
      "\n\nevent: content_block_delta\ndata: " +
      """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}""" +
      "\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"

  private val rateLimitBody =
    """{"type":"error","error":{"type":"rate_limit_error","message":"Number of requests exceeded"}}"""

  private val decisionBody =
    """{"model":"jev-1.12","answers":{"is_urgent":{"type":"noul","noul":0.999}},"usage":{"input_tokens":312,"output_tokens":48}}"""

  private val fakeCliScript =
    """#!/bin/sh
      |printf '%s\n' '{"type":"system","subtype":"init","apiKeySource":"none","model":"fake-model","tools":[],"permissionMode":"default","session_id":"session-1","cwd":"/tmp","claude_code_version":"test"}'
      |while IFS= read -r line; do
      |  case "$line" in
      |    *'"type":"user"'*)
      |      printf '%s\n' '{"type":"result","subtype":"success","result":"ok","total_cost_usd":0.0,"num_turns":1,"duration_ms":1,"is_error":false,"session_id":"session-1"}'
      |      ;;
      |  esac
      |done
      |""".stripMargin

  private lazy val fakeCli: Path = {
    val file = Files.createTempFile("fake-claude-cli", ".sh")
    Files.write(file, fakeCliScript.getBytes(StandardCharsets.UTF_8))
    file.toFile.setExecutable(true)
    file
  }

  private def reply(
    exchange: HttpExchange,
    status: Int,
    body: String,
    contentType: String
  ): Unit = {
    val in = exchange.getRequestBody
    while (in.read() != -1) {}
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", contentType)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
    exchange.close()
  }

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/messages",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          messageRequests.incrementAndGet()
          if (rateLimited)
            reply(exchange, 429, rateLimitBody, "application/json")
          else
            reply(exchange, 200, messageStream, "text/event-stream")
        }
      }
    )
    server.createContext(
      "/v1/systemone",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          decisionRequests.incrementAndGet()
          reply(exchange, 200, decisionBody, "application/json")
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    run(anthropic.close)
    run(decisions.close)
    plainEngine.close()
    server.stop(0)
    Files.deleteIfExists(fakeCli)
  }

  private def stubService(
    closed: AtomicInteger,
    model: String
  ): TypeSafeService =
    new TypeSafeService {
      override val defaultModel: String = model

      override def systemOne(
        state: JsValue,
        questions: Map[String, Question],
        model: String
      ): Future[SystemOneResponse] =
        Future.failed(new UnsupportedOperationException("a stub"))

      override def systemOne(
        state: String,
        questions: Map[String, Question]
      ): Future[SystemOneResponse] =
        Future.failed(new UnsupportedOperationException("a stub"))

      override def systemOne(
        state: String,
        questions: Map[String, Question],
        model: String
      ): Future[SystemOneResponse] =
        Future.failed(new UnsupportedOperationException("a stub"))

      override def listModels: Future[Seq[ModelMetadata]] =
        Future.successful(Nil)

      override def close(): Unit = {
        closed.incrementAndGet()
        ()
      }
    }

  private def run[A](effect: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe(implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    )

  private def runExit[A](effect: ZIO[Any, Throwable, A]): Exit[Throwable, A] =
    Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(effect))

  private val messages = Seq(UserMessage("hi"))
  private val settings = AnthropicCreateMessageSettings("claude-sonnet-5", max_tokens = 100)
  private val questions: Map[String, Question] =
    Map("is_urgent" -> NoulQuestion("The message conveys urgency or time-sensitivity"))

  "the Anthropic view" should {

    "stream a message as a cold ZStream" in {
      rateLimited = false
      val before = messageRequests.get
      val stream = anthropic.createMessageStreamed(messages, settings)

      messageRequests.get shouldBe before

      run(stream.runCollect).map(_.text).mkString shouldBe "Hello"
      run(stream.runCollect).size shouldBe 1
      messageRequests.get shouldBe before + 2
    }

    "fail a stream with Anthropic's classified exception" in {
      rateLimited = true
      val exit =
        try runExit(anthropic.createMessageStreamed(messages, settings).runCollect)
        finally rateLimited = false

      exit match {
        case Exit.Failure(cause) =>
          cause.failureOption.map(_.getClass) shouldBe Some(
            classOf[AnthropicScalaRateLimitException]
          )
        case Exit.Success(deltas) =>
          fail(s"Expected a rate limit failure, got $deltas")
      }
    }
  }

  "the decision service view" should {

    "ask as a lazy Task - a request per run" in {
      val before = decisionRequests.get
      val task = decisions.systemOne("Please help ASAP.", questions)

      decisionRequests.get shouldBe before

      run(task).model shouldBe "jev-1.12"
      run(task).answers.keySet shouldBe Set("is_urgent")
      decisionRequests.get shouldBe before + 2
    }

    "pass the members that are no calls through" in {
      decisions.defaultModel shouldBe "jev-latest"
      decisions.asFuture.defaultModel shouldBe "jev-latest"
    }

    "close the service with its layer's scope" in {
      val closed = new AtomicInteger(0)
      val stub = stubService(closed, "stub-model")

      val model = run(
        ZIO
          .serviceWith[TypeSafeServiceZIO](_.defaultModel)
          .provide(TypeSafeServiceZIO.layer(() => stub))
      )

      model shouldBe "stub-model"
      closed.get shouldBe 1

      // built by an effect from the environment (e.g. a service on an engine of the layer's)
      val fromEffect = run(
        ZIO
          .serviceWith[TypeSafeServiceZIO](_.defaultModel)
          .provide(
            ZLayer.succeed("env-model"),
            TypeSafeServiceZIO.layerFromZIO(ZIO.serviceWith[String](stubService(closed, _)))
          )
      )

      fromEffect shouldBe "env-model"
      closed.get shouldBe 2
    }
  }

  "the Claude agent view" should {

    "report readiness and carry a turn's events on its hot ZStream" in {
      val agent = ClaudeAgentServiceZIO(
        ClaudeAgentServiceFactory.startSession(
          ClaudeAgentSettings(executablePath = Some(fakeCli.toString))
        )
      )

      def turnEnded(event: ClaudeAgentEvent): Boolean =
        event match {
          case _: ClaudeAgentEvent.ResultSuccess => true
          case _: ClaudeAgentEvent.ResultError   => true
          case _                                 => false
        }

      // the stream is hot: the turn is sent only once the run is subscribed, which its first
      // element (the replayed init) shows
      val turn =
        for {
          subscribed <- Promise.make[Nothing, Unit]
          running <- agent.events
            .tap(_ => subscribed.succeed(()))
            .takeUntil(turnEnded)
            .runCollect
            .fork
          init <- agent.ready
          _ <- subscribed.await
          _ <- agent.send("hello")
          events <- running.join
        } yield (init, events)

      val (init, events) =
        try
          run(turn.timeoutFail(new RuntimeException("The agent turn timed out"))(30.seconds))
        finally run(agent.close)

      init.model shouldBe "fake-model"
      events.lastOption.exists(turnEnded) shouldBe true
    }
  }
}
