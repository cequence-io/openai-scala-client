package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaServerErrorException
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateSpeechSettings,
  VoiceType
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.testkit.{StreamSpecSupport, TestEngines}
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.stream.Bytes
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * The 2.0 stream semantics against a local server, on the engine and consumer under test
 * (`-Dws-client.engine`, `-Dopenai.test.consumer`):
 *   - COLD: building a stream sends nothing; every run is a new request (a re-run is billed
 *     again), and a stream that is never run sends nothing at all
 *   - the neutral consumer runs a stream of the JDK engine without any bridge
 *   - downloads are binary-safe (every byte value survives), a missing file is `None`, any
 *     other status a classified failure
 */
class ColdStreamsWireSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with StreamSpecSupport {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val requests = new AtomicInteger(0)

  // every byte value, twice - in a body no text decoding may touch
  private val allBytes: Array[Byte] = (0 until 512).map(i => (i % 256).toByte).toArray

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = TestEngines.streaming()
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val service =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)

  private val chatChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""
  private val finishChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val path = exchange.getRequestURI.getPath

          val (status, contentType, body) =
            if (path.endsWith("/chat/completions")) {
              requests.incrementAndGet()
              (
                200,
                "text/event-stream",
                s"data: $chatChunk\n\ndata: $finishChunk\n\ndata: [DONE]\n\n"
                  .getBytes(StandardCharsets.UTF_8)
              )
            } else if (
              path.endsWith("/files/file-1/content") || path.endsWith("/audio/speech")
            )
              (200, "application/octet-stream", allBytes)
            else if (path.endsWith("/files/missing/content"))
              (404, "application/json", """{"error":{"message":"No such file"}}""".getBytes)
            else
              (502, "text/plain", "Bad Gateway".getBytes)

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

  "a streamed chat completion" should {

    "send nothing until it is run, and a new request per run" in {
      requests.set(0)
      val stream = service.createChatCompletionStreamed(messages, settings)
      val typed = service.createChatCompletionStreamedTyped(messages, settings)

      Thread.sleep(200)
      requests.get shouldBe 0

      val first = runStream(stream)
      val second = runStream(stream)
      requests.get shouldBe 2
      first.flatMap(_.choices.flatMap(_.delta.content)) shouldBe Seq("Hello")
      second shouldBe first

      runStream(typed).collect { case ChatChunk.Text(text) => text } shouldBe Seq("Hello")
      requests.get shouldBe 3
    }

    "send nothing at all when it is never run" in {
      requests.set(0)
      (1 to 5).foreach(_ => service.createChatCompletionStreamed(messages, settings))

      Thread.sleep(200)
      requests.get shouldBe 0
    }

    "run on the neutral consumer without a bridge" in {
      assume(
        streamRunner.name == "neutral",
        "a native consumer bridges a JDK-engine stream once"
      )
      val (chunks, bridges) =
        runStreamCounted(service.createChatCompletionStreamed(messages, settings))

      chunks should not be empty
      bridges shouldBe 0
    }
  }

  "a download" should {

    "keep every byte value - file content and generated audio" in {
      val content =
        Await.result(service.retrieveFileContentAsSource("file-1"), 20.seconds).get
      runStream(content).foldLeft(Bytes.empty)(_ ++ _).toArray.toSeq shouldBe allBytes.toSeq

      val speech =
        Await.result(
          service.createAudioSpeech("hi", CreateSpeechSettings("tts-1", VoiceType.alloy)),
          20.seconds
        )
      runStream(speech, 20.seconds).foldLeft(Bytes.empty)(_ ++ _).toArray.toSeq shouldBe
        allBytes.toSeq
    }

    "be None for a missing file and a classified failure for any other status" in {
      Await.result(service.retrieveFileContentAsSource("missing"), 20.seconds) shouldBe None

      intercept[OpenAIScalaServerErrorException](
        Await.result(service.retrieveFileContentAsSource("broken"), 20.seconds)
      )
    }
  }
}
