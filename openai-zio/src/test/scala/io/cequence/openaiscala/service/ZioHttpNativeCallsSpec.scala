package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtraZIO._
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtraZIO._
import io.cequence.openaiscala.typesafe.service.{TypeSafeServiceFactory, TypeSafeServiceZIO}
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.stream.CallDiagnostics
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Format, Json}
import zio.{Runtime, Unsafe, ZIO}

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext

final case class NativeCallsCity(name: String)

object NativeCallsCity {
  implicit val format: Format[NativeCallsCity] = Json.format[NativeCallsCity]
}

final case class NativeCallsUrgency(is_urgent: Boolean)

object NativeCallsUrgency {
  implicit val format: Format[NativeCallsUrgency] = Json.format[NativeCallsUrgency]
}

/**
 * Native plain calls (since 2.1.0): on the zio-http engine a plain call of a ZIO view is the
 * engine's own effect - no Future in between (no bridge, counted with
 * `CallDiagnostics.counted`), so interrupting the Task aborts the HTTP request. Chat and tool
 * completion (the chat completions API and the Responses route), the JSON helper and the
 * decision routines; the JDK engine (Future-based) as the control - one bridge.
 */
class ZioHttpNativeCallsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val chatBody =
    """{"id":"chatcmpl_1","object":"chat.completion","created":1700000000,"model":"gpt-5.4",
      |"choices":[{"index":0,"message":{"role":"assistant","content":"{\"name\":\"Oslo\"}"},"finish_reason":"stop"}],
      |"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""".stripMargin

  private val responsesBody =
    """{"id":"resp_1","object":"response","created_at":1700000000,"status":"completed","model":"gpt-6-astra",
      |"output":[{"type":"message","id":"msg_1","status":"completed","role":"assistant","content":[{"type":"output_text","text":"hi","annotations":[]}]}],
      |"parallel_tool_calls":true,"text":{"format":{"type":"text"}},
      |"usage":{"input_tokens":10,"input_tokens_details":{"cached_tokens":0},"output_tokens":2,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":12}}""".stripMargin

  private val decisionBody =
    """{"model":"jev-1.14.0","answers":{"is_urgent":{"type":"noul","noul":0.999}},"usage":{"input_tokens":312,"output_tokens":0}}"""

  // a re-ranking batch asks p0, p1, ...
  private val rerankBody =
    """{"model":"jev-1.14.0","answers":{"p0":{"type":"noul","noul":0.97}},"usage":{"input_tokens":120,"output_tokens":0}}"""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val baseUrl = s"http://localhost:${server.getAddress.getPort}"

  private val zioHttp =
    WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("zio-http"))
  private val jdk = WSClientEngineRegistry(TransportSettings(), Some("jdk"))

  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  // the merged (sync + streamed) full service - its calls are the wrapped service's own
  private lazy val service = OpenAIStreamedServiceZIO(
    OpenAIServiceFactory.withStreaming.customEngineInstance(zioHttp, s"$baseUrl/", context)
  )
  private lazy val onJdk = OpenAIServiceZIO(
    OpenAIServiceFactory.customEngineInstance(jdk, s"$baseUrl/", context)
  )
  private lazy val slow = OpenAIServiceZIO(
    OpenAIServiceFactory.customEngineInstance(zioHttp, s"$baseUrl/slow/", context)
  )
  private lazy val decisions = TypeSafeServiceZIO(
    TypeSafeServiceFactory.withEngine(
      zioHttp,
      apiKey = "test-key",
      baseUrl = baseUrl,
      defaultModel = "jev-latest"
    )
  )

  private val slowRequests = new AtomicInteger(0)
  @volatile private var slowReceived = new CountDownLatch(1)
  @volatile private var slowAborted = new CountDownLatch(1)

  override protected def beforeAll(): Unit = {
    server.createContext("/chat/completions", reply(chatBody))
    server.createContext("/responses", reply(responsesBody))
    server.createContext(
      "/v1/systemone",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val request =
            new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val body =
            if (request.contains("\"p0\""))
              rerankBody
            else
              decisionBody
          send(exchange, body)
        }
      }
    )
    // headers at once, then a byte every 50 ms: the client waits for the whole body, so the
    // request stays in flight until the client lets go - a write fails once it has
    server.createContext(
      "/slow/chat/completions",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          slowRequests.incrementAndGet()
          exchange.getRequestBody.readAllBytes()
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(200, 0)
          slowReceived.countDown()
          val out = exchange.getResponseBody
          try {
            (1 to 300).foreach { _ =>
              out.write(' '.toInt)
              out.flush()
              Thread.sleep(50)
            }
          } catch {
            case _: IOException => slowAborted.countDown()
          } finally exchange.close()
        }
      }
    )
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool())
    server.start()
  }

  override protected def afterAll(): Unit = {
    run(service.close)
    run(onJdk.close)
    run(slow.close)
    run(decisions.close)
    zioHttp.close()
    jdk.close()
    server.stop(0)
  }

  private def reply(body: String): HttpHandler =
    new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        exchange.getRequestBody.readAllBytes()
        send(exchange, body)
      }
    }

  private def send(
    exchange: HttpExchange,
    body: String
  ): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(200, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
    exchange.close()
  }

  private def run[A](effect: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe(implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    )

  private val messages = Seq(UserMessage("hi"))
  private val gpt54 = CreateChatCompletionSettings(ModelId.gpt_5_4)
  private val weather = FunctionTool(
    "get_weather",
    parameters = JsonSchema.Object(properties = Seq("location" -> JsonSchema.String()))
  )

  "a plain call of the ZIO view on the zio-http engine" should {

    "complete a chat with no bridge" in {
      val (task, bridges) =
        CallDiagnostics.counted(service.createChatCompletion(messages, gpt54))

      run(task).contentHead shouldBe """{"name":"Oslo"}"""
      bridges.get shouldBe 0
    }

    "complete a tool call through the Responses route (GPT-6 Astra) with no bridge" in {
      val (task, bridges) = CallDiagnostics.counted(
        service.createChatToolCompletion(
          messages,
          Seq(weather),
          None,
          CreateChatCompletionSettings(ModelId.gpt_6_astra)
        )
      )

      run(task).choices.head.message.content shouldBe Some("hi")
      bridges.get shouldBe 0
    }

    "run the JSON helper with no bridge" in {
      val (task, bridges) = CallDiagnostics.counted(
        service.createChatCompletionWithJSON[NativeCallsCity](messages, gpt54)
      )

      run(task) shouldBe NativeCallsCity("Oslo")
      bridges.get shouldBe 0
    }

    "decide a case class with no bridge" in {
      val (task, bridges) =
        CallDiagnostics.counted(decisions.decide[NativeCallsUrgency]("The server is down!"))

      run(task).value shouldBe NativeCallsUrgency(is_urgent = true)
      bridges.get shouldBe 0
    }

    "re-rank passages with no bridge" in {
      val (task, bridges) = CallDiagnostics.counted(
        decisions.rerank("Is it urgent?", Seq("The server is down!"))
      )

      val ranked = run(task)
      ranked.map(_.item) shouldBe Seq("The server is down!")
      ranked.map(_.score) shouldBe Seq(0.97)
      bridges.get shouldBe 0
    }

    "abort the request when the Task is interrupted" in {
      slowReceived = new CountDownLatch(1)
      slowAborted = new CountDownLatch(1)
      val before = slowRequests.get()

      // interrupted once the request is in flight - the server has it and sends the body
      val exit = run(
        for {
          fiber <- slow.createChatCompletion(messages, gpt54).fork
          _ <- ZIO.attemptBlocking(slowReceived.await(20, TimeUnit.SECONDS))
          exit <- fiber.interrupt
        } yield exit
      )

      exit.isInterrupted shouldBe true
      slowRequests.get() shouldBe before + 1
      // the server's next write fails: the client closed the connection
      slowAborted.await(10, TimeUnit.SECONDS) shouldBe true
    }
  }

  "a plain call of the ZIO view on the JDK engine (Future-based)" should {

    "cost one bridge" in {
      val (task, bridges) =
        CallDiagnostics.counted(onJdk.createChatCompletion(messages, gpt54))

      run(task).contentHead shouldBe """{"name":"Oslo"}"""
      bridges.get shouldBe 1
    }
  }
}
