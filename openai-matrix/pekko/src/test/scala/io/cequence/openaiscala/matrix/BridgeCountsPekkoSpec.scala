package io.cequence.openaiscala.matrix

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.ConfigFactory
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionChunkResponse}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.stream.SourceInterop._
import io.cequence.wsclient.stream.{AsyncStream, Bytes, StreamDiagnostics}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Bridges (hops through `Flow.Publisher`, counted by `StreamDiagnostics.counted`) of a Pekko
 * consumer (`.toSource`) per engine, each engine picked by its id (whatever
 * `-Dws-client.engine` says): none on both Pekko engines, play-pekko-stream and pekko-http - a
 * chat stream, the typed stream and a download (its preflight included) stay Pekko; one per
 * HTTP response on the jdk engine (the engine's publisher).
 */
class BridgeCountsPekkoSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val timeout = 30.seconds

  // daemonic: the forked test JVM exits even if a run is left hanging
  private val system = ActorSystem(
    "bridge-counts-pekko",
    ConfigFactory.parseString("pekko.daemonic = on").withFallback(ConfigFactory.load())
  )
  private val materializer: Materializer = Materializer(system)

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

  private val pekkoEngineIds = Seq("play-pekko-stream", "pekko-http")

  private val engines =
    (pekkoEngineIds :+ "jdk").map { id =>
      id -> WSClientEngineRegistry.outputStreamed(TransportSettings(), Some(id))
    }.toMap

  private lazy val services = engines.map { case (id, engine) =>
    id -> OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  }

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
    services.values.foreach(_.close())
    engines.values.foreach(_.close())
    server.stop(0)
    Await.result(system.terminate(), timeout)
    ()
  }

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gpt-5.4")

  // compiled into a Pekko Source inside the counter's scope, run, counted once the run is over
  private def runCounted[A](stream: AsyncStream[A]): (immutable.Seq[A], Long) = {
    val (source, bridges) = StreamDiagnostics.counted(stream.toSource)
    val elements = Await.result(source.runWith(Sink.seq)(materializer), timeout)
    (elements, bridges.get)
  }

  private def contents(chunks: Seq[ChatCompletionChunkResponse]): Seq[String] =
    chunks.flatMap(_.choices.flatMap(_.delta.content))

  pekkoEngineIds.foreach { id =>
    s"the $id engine with a Pekko consumer" should {

      "stream a chat completion with no bridge" in {
        val (chunks, bridges) =
          runCounted(services(id).createChatCompletionStreamed(messages, settings))

        contents(chunks) shouldBe Seq("Hello")
        bridges shouldBe 0
      }

      "stream the typed chunks with no bridge" in {
        val (chunks, bridges) =
          runCounted(services(id).createChatCompletionStreamedTyped(messages, settings))

        chunks.collect { case ChatChunk.Text(text) => text } shouldBe Seq("Hello")
        bridges shouldBe 0
      }

      "download every byte value with no bridge, its preflight included" in {
        // the download is preflighted when it is called (a 404 is None before any run),
        // natively on the engine's materializer
        val (download, preflightBridges) =
          StreamDiagnostics.counted(services(id).retrieveFileContentAsSource("file-1"))
        val body = Await.result(download, timeout).getOrElse(fail("The file was not found"))

        val (chunks, bridges) = runCounted(body)

        chunks.foldLeft(Bytes.empty)(_ ++ _).toArray.toSeq shouldBe allBytes.toSeq
        preflightBridges.get shouldBe 0
        bridges shouldBe 0
      }
    }
  }

  "the jdk engine with a Pekko consumer" should {

    "cross one bridge per HTTP response" in {
      val stream = services("jdk").createChatCompletionStreamed(messages, settings)

      val (chunks, bridges) = runCounted(stream)
      contents(chunks) shouldBe Seq("Hello")
      bridges shouldBe 1

      chatRequests.set(0)
      val (twice, twiceBridges) = runCounted(stream.concat(stream))
      contents(twice) shouldBe Seq("Hello", "Hello")
      chatRequests.get shouldBe 2
      twiceBridges shouldBe 2
    }
  }
}
