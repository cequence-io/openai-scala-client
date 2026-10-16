package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Comment frames in a chat-completion stream, against a local server. While a slow model
 * works, OpenRouter sends `: OPENROUTER PROCESSING` before the first chunk and between chunks,
 * and up to ws-client 1.1.2 each one failed the whole stream ("response is not a JSON");
 * 1.1.3 skips them. Replays a stream recorded from OpenRouter (`meta/muse-wonder`, 2026-10-16:
 * five keep-alives, a reasoning chunk, three more, the answer; the provider's encrypted
 * reasoning replaced).
 */
class StreamedKeepAliveWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("streamed-keep-alive-wire")
  private implicit val materializer: Materializer = Materializer(system)

  @volatile private var sse: String = ""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())

  private lazy val service = OpenAIServiceFactory.withStreaming.customEngineInstance(
    engine,
    s"http://localhost:${server.getAddress.getPort}/",
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val bytes = sse.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "text/event-stream")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
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
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def run[T](stream: Source[T, _]): Seq[T] =
    Await.result(stream.runWith(Sink.seq), 30.seconds)

  private def recorded: String = {
    val source =
      scala.io.Source
        .fromResource("openrouter/muse-wonder-keep-alive.sse")(scala.io.Codec.UTF8)
    try source.mkString
    finally source.close()
  }

  private def chatChunk(content: String) =
    s"""{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"meta/muse-wonder","choices":[{"index":0,"delta":{"role":"assistant","content":"$content"},"finish_reason":null}]}"""

  private val messages =
    Seq(UserMessage("How many primes are below 300? Answer with the number only."))

  private val settings = CreateChatCompletionSettings("meta/muse-wonder")

  "A chat-completion stream with keep-alive comments" should {

    "skip them in the OpenAI-shaped stream" in {
      sse = recorded

      run(service.createChatCompletionStreamed(messages, settings))
        .flatMap(_.choices.flatMap(_.delta.content))
        .mkString shouldBe "62"
    }

    "skip them in the typed stream - the reasoning, the answer and the finish come through" in {
      sse = recorded

      val chunks = run(service.createChatCompletionStreamedTyped(messages, settings))

      chunks.collect { case ChatChunk.Text(text) => text }.mkString shouldBe "62"
      chunks.collect { case ChatChunk.Thinking(text) => text }.mkString should startWith(
        "Counting primes"
      )
      chunks.collect { case ChatChunk.Finish(reason, _) => reason } should contain(
        FinishReason.stop
      )
    }

    "skip a bare `:` frame and a comment line inside an event" in {
      sse = ":\n\n" +
        s"data: ${chatChunk("6")}\n: OPENROUTER PROCESSING\n\n" +
        s"data: ${chatChunk("2")}\n\n" +
        "data: [DONE]\n\n"

      run(service.createChatCompletionStreamed(messages, settings))
        .flatMap(_.choices.flatMap(_.delta.content))
        .mkString shouldBe "62"
    }
  }
}
