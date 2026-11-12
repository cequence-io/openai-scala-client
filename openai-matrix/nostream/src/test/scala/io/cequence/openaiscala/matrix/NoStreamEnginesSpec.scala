package io.cequence.openaiscala.matrix

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.ConfigFactory
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.testkit.StreamSpecSupport
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.JdkWSClientEngine
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * The engines without output streaming - sttp and play-akka, each picked by its id (the
 * project pins `-Dws-client.engine=sttp`): a streamed service refuses them before any request,
 * naming the engines that stream; a plain service makes its sync calls on them, and its
 * downloads are binary-safe, buffered as one chunk (a missing file is `None`). Auto-selection
 * of a streaming engine passes them over for jdk, although both have the higher priority.
 */
class NoStreamEnginesSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with StreamSpecSupport {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val timeout = 30.seconds

  private val requests = new AtomicInteger(0)

  // every byte value - in a body no text decoding may touch
  private val allBytes: Array[Byte] = (0 until 256).map(_.toByte).toArray

  private val chatCompletion =
    """{"id":"chat-1","object":"chat.completion","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
  private val chatChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private val engineIds = Seq("sttp", "play-akka")

  private val engines =
    engineIds.map(id => id -> WSClientEngineRegistry(TransportSettings(), Some(id))).toMap

  private lazy val services = engines.map { case (id, engine) =>
    id -> OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
  }

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          requests.incrementAndGet()
          val request =
            new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val path = exchange.getRequestURI.getPath

          val (status, contentType, body) =
            if (path.endsWith("/chat/completions") && request.contains("\"stream\":true"))
              (
                200,
                "text/event-stream",
                s"data: $chatChunk\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)
              )
            else if (path.endsWith("/chat/completions"))
              (200, "application/json", chatCompletion.getBytes(StandardCharsets.UTF_8))
            else if (path.endsWith("/files/file-1/content"))
              (200, "application/octet-stream", allBytes)
            else
              (404, "application/json", """{"error":{"message":"Not found"}}""".getBytes)

          exchange.getResponseHeaders.add("Content-Type", contentType)
          exchange.sendResponseHeaders(status, body.length.toLong)
          exchange.getResponseBody.write(body)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    services.values.foreach(_.close())
    engines.values.foreach(_.close())
    server.stop(0)
    ()
  }

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gpt-5.4")

  // the project pins -Dws-client.engine=sttp, which wins over auto-selection: it is lifted for
  // `body` (the only suite of this forked JVM, its tests run one at a time)
  private def withoutConfiguredEngine[T](body: => T): T = {
    val key = WSClientEngineRegistry.ConfigKey
    val pinned = Option(System.clearProperty(key))
    ConfigFactory.invalidateCaches()
    try body
    finally {
      pinned.foreach(System.setProperty(key, _))
      ConfigFactory.invalidateCaches()
    }
  }

  engineIds.foreach { id =>
    s"the $id engine (no output streaming)" should {

      "be refused by a streamed service before any request, naming the engines that stream" in {
        requests.set(0)

        val error = intercept[OpenAIScalaClientException](
          OpenAIServiceFactory.withStreaming
            .customEngineInstance(engines(id), coreUrl, context)
        )

        Seq("jdk", "zio-http", "play-akka-stream", "play-pekko-stream", "pekko-http").foreach {
          name =>
            error.getMessage should include(name)
        }
        error.getMessage should include(engines(id).getClass.getName)
        requests.get shouldBe 0
      }

      "serve a plain service's chat completion" in {
        val response =
          Await.result(services(id).createChatCompletion(messages, settings), timeout)

        response.contentHead shouldBe "Hi"
      }

      "download every byte value buffered as one chunk, and a missing file as None" in {
        val body = Await
          .result(services(id).retrieveFileContentAsSource("file-1"), timeout)
          .getOrElse(fail("The file was not found"))

        runStream(body).map(_.toArray.toSeq) shouldBe Seq(allBytes.toSeq)
        // buffered, so the stream can be run again
        runStream(body).map(_.toArray.toSeq) shouldBe Seq(allBytes.toSeq)

        Await.result(
          services(id).retrieveFileContentAsSource("missing"),
          timeout
        ) shouldBe None
      }
    }
  }

  "auto-selection of a streaming engine" should {

    "pick jdk although sttp and play-akka have the higher priority" in {
      val priorities =
        WSClientEngineRegistry.providers
          .map(provider => provider.engineId -> provider.priority)
          .toMap
      priorities("sttp") should be > priorities("jdk")
      priorities("play-akka") should be > priorities("jdk")

      withoutConfiguredEngine {
        // plain calls go through the highest priority
        WSClientEngineRegistry.provider(None).engineId shouldBe "play-akka"

        val engine = WSClientEngineRegistry.outputStreamed()
        try {
          engine shouldBe a[JdkWSClientEngine]

          val service =
            OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
          try {
            val chunks = runStream(service.createChatCompletionStreamed(messages, settings))
            chunks.flatMap(_.choices.flatMap(_.delta.content)) shouldBe Seq("Hello")
          } finally service.close()
        } finally engine.close()
      }
    }
  }
}
