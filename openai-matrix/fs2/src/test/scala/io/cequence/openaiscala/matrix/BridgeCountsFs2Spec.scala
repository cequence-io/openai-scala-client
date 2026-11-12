package io.cequence.openaiscala.matrix

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionChunkResponse}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.stream.Fs2Interop._
import io.cequence.wsclient.stream.{AsyncStream, Bytes, StreamDiagnostics}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Bridges (hops through `Flow.Publisher`, counted by `StreamDiagnostics.counted`) of an fs2
 * consumer (`.toFs2[IO]`) on the jdk engine, picked by its id (whatever `-Dws-client.engine`
 * says): one per HTTP response - the engine's publisher, or the neutral run a download is
 * preflighted on - and the elements arrive as sent.
 */
class BridgeCountsFs2Spec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val runtime: IORuntime = IORuntime.global

  private val timeout = 30.seconds

  private val chatRequests = new AtomicInteger(0)

  // every byte value - in a body no text decoding may touch
  private val allBytes: Array[Byte] = (0 until 256).map(_.toByte).toArray

  private val chatChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""
  private val finishChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private val engine = WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("jdk"))

  private lazy val service =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val path = exchange.getRequestURI.getPath

          val (status, contentType, body) =
            if (path.endsWith("/chat/completions")) {
              chatRequests.incrementAndGet()
              (
                200,
                "text/event-stream",
                s"data: $chatChunk\n\ndata: $finishChunk\n\ndata: [DONE]\n\n"
                  .getBytes(StandardCharsets.UTF_8)
              )
            } else if (path.endsWith("/files/file-1/content"))
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
    service.close()
    engine.close()
    server.stop(0)
    ()
  }

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gpt-5.4")

  // compiled into an fs2 stream inside the counter's scope, run, counted once the run is over
  private def runCounted[A](stream: AsyncStream[A]): (List[A], Long) = {
    val (compiled, bridges) = StreamDiagnostics.counted(stream.toFs2[IO])
    val elements = Await.result(compiled.compile.toList.unsafeToFuture()(runtime), timeout)
    (elements, bridges.get)
  }

  private def contents(chunks: Seq[ChatCompletionChunkResponse]): Seq[String] =
    chunks.flatMap(_.choices.flatMap(_.delta.content))

  "the jdk engine with an fs2 consumer" should {

    "cross one bridge for a chat stream, its chunks as sent" in {
      val (chunks, bridges) =
        runCounted(service.createChatCompletionStreamed(messages, settings))

      chunks.map(_.id) shouldBe Seq("c1", "c1")
      contents(chunks) shouldBe Seq("Hello")
      chunks.flatMap(_.choices.flatMap(_.finish_reason)) shouldBe Seq("stop")
      bridges shouldBe 1
    }

    "cross one bridge for the typed stream" in {
      val (chunks, bridges) =
        runCounted(service.createChatCompletionStreamedTyped(messages, settings))

      chunks.collect { case ChatChunk.Text(text) => text } shouldBe Seq("Hello")
      bridges shouldBe 1
    }

    "cross one bridge per HTTP response" in {
      val stream = service.createChatCompletionStreamed(messages, settings)
      chatRequests.set(0)

      val (chunks, bridges) = runCounted(stream.concat(stream))

      contents(chunks) shouldBe Seq("Hello", "Hello")
      chatRequests.get shouldBe 2
      bridges shouldBe 2
    }

    "cross one bridge for a download, every byte value intact" in {
      // preflighted when it is called, on the neutral interpreter (no bridge); the preflighted
      // run is that interpreter's, so fs2 bridges it once
      val (download, preflightBridges) =
        StreamDiagnostics.counted(service.retrieveFileContentAsSource("file-1"))
      val body = Await.result(download, timeout).getOrElse(fail("The file was not found"))

      val (chunks, bridges) = runCounted(body)

      chunks.foldLeft(Bytes.empty)(_ ++ _).toArray.toSeq shouldBe allBytes.toSeq
      preflightBridges.get shouldBe 0
      bridges shouldBe 1
    }
  }
}
