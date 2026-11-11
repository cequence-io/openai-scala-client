package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.stream.StreamDiagnostics
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.{Runtime, Unsafe, ZIO}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Native ZIO in, native ZIO out: on the zio-http engine a service stream is the engine's own
 * ZStream, so the ZIO view hands it on with our operators as ZIO stages - no bridge through
 * `Flow.Publisher` (counted with `StreamDiagnostics.counted`) - typed chat chunks and raw
 * download bytes alike.
 */
class ZioHttpNativeRoundTripSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val allBytes: Array[Byte] = (0 until 512).map(i => (i % 256).toByte).toArray

  private val chunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine =
    WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("zio-http"))
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"

  private lazy val futureService = OpenAIServiceFactory.withStreaming.customEngineInstance(
    engine,
    coreUrl,
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )
  private lazy val service = OpenAIStreamedServiceZIO(futureService)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val (contentType, body) =
            if (exchange.getRequestURI.getPath.endsWith("/chat/completions"))
              (
                "text/event-stream",
                s"data: $chunk\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)
              )
            else
              ("application/octet-stream", allBytes)
          exchange.getResponseHeaders.add("Content-Type", contentType)
          exchange.sendResponseHeaders(200, body.length.toLong)
          exchange.getResponseBody.write(body)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    futureService.close()
    engine.close()
    server.stop(0)
    ()
  }

  private def run[A](effect: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe(implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    )

  "the ZIO view on the zio-http engine" should {

    "stream typed chat chunks with no bridge" in {
      val (stream, bridges) = StreamDiagnostics.counted(
        service.createChatCompletionStreamedTyped(
          Seq(UserMessage("hi")),
          CreateChatCompletionSettings("gpt-5.4")
        )
      )

      run(stream.collect { case ChatChunk.Text(text) => text }.runCollect).toList shouldBe
        List("Hello")
      bridges.get shouldBe 0
    }

    "stream download bytes with every byte value intact" in {
      val body =
        Await.result(futureService.retrieveFileContentAsSource("file-1"), 20.seconds).get
      val (bytes, bridges) = StreamDiagnostics.counted(ZIOConversions.bytes(body))

      run(bytes.runCollect).toArray.toSeq shouldBe allBytes.toSeq

      // a download is preflighted (started up front, so a 404 is None before the stream is
      // returned), and the preflight keeps the engine's native leaf
      bridges.get shouldBe 0
    }

    "convert a raw engine byte stream with no bridge" in {
      val raw = engine.execRawAsyncStream(
        io.cequence.wsclient.domain.SiteBinding(coreUrl, WsRequestContext()),
        "files/file-1/content",
        "GET"
      )
      val (bytes, bridges) = StreamDiagnostics.counted(ZIOConversions.bytes(raw))

      run(bytes.runCollect).toArray.toSeq shouldBe allBytes.toSeq
      bridges.get shouldBe 0
    }
  }
}
