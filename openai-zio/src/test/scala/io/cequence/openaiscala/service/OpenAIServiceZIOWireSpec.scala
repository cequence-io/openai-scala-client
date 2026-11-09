package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaRateLimitException
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool, UserMessage}
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionExtraZIO._
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.testkit.TestEngines
import io.cequence.wsclient.domain.WsRequestContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Format, Json}
import zio.{Exit, Runtime, Unsafe, ZIO}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

/**
 * The generated ZIO view against a local server: calls are lazy Tasks (nothing is sent until
 * one runs, a new request per run), streams ZStreams, downloads byte ZStreams, failures the
 * classified exceptions, the JSON helpers ZIO-native too, and the layer closes the service.
 */
final case class City(city: String)

object City {
  implicit val format: Format[City] = Json.format[City]
}

class OpenAIServiceZIOWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  private val chatRequests = new AtomicInteger(0)
  @volatile private var rateLimited = false

  private val allBytes: Array[Byte] = (0 until 512).map(i => (i % 256).toByte).toArray

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = TestEngines.streaming()
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val service = OpenAIStreamedServiceZIO(
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  )

  private def completion(content: String) =
    Json
      .obj(
        "id" -> "c1",
        "object" -> "chat.completion",
        "created" -> 1700000000,
        "model" -> "gpt-5.4",
        "choices" -> Json.arr(
          Json.obj(
            "index" -> 0,
            "message" -> Json.obj("role" -> "assistant", "content" -> content),
            "finish_reason" -> "stop"
          )
        ),
        "usage" -> Json
          .obj("prompt_tokens" -> 1, "completion_tokens" -> 1, "total_tokens" -> 2)
      )
      .toString

  private val chunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val request =
            new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val path = exchange.getRequestURI.getPath

          val (status, contentType, body) =
            if (path.endsWith("/chat/completions")) {
              chatRequests.incrementAndGet()
              if (rateLimited)
                (
                  429,
                  "application/json",
                  """{"error":{"message":"Rate limit reached","type":"requests"}}""".getBytes
                )
              else if (request.contains("\"stream\":true"))
                (
                  200,
                  "text/event-stream",
                  s"data: $chunk\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)
                )
              else if (request.contains("json"))
                (200, "application/json", completion("""{"city":"Oslo"}""").getBytes)
              else
                (200, "application/json", completion("Hi").getBytes)
            } else if (path.endsWith("/files/file-1/content"))
              (200, "application/octet-stream", allBytes)
            else
              (404, "application/json", """{"error":{"message":"No such file"}}""".getBytes)

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
    run(service.close)
    engine.close()
    server.stop(0)
    ()
  }

  private def run[A](effect: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe(implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    )

  private def runExit[A](effect: ZIO[Any, Throwable, A]): Exit[Throwable, A] =
    Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(effect))

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gpt-5.4")

  "the ZIO view of the OpenAI service" should {

    "answer a call as a lazy Task - nothing sent until it runs, a request per run" in {
      chatRequests.set(0)
      val task = service.createChatCompletion(messages, settings)

      Thread.sleep(200)
      chatRequests.get shouldBe 0

      run(task).contentHead shouldBe "Hi"
      run(task).contentHead shouldBe "Hi"
      chatRequests.get shouldBe 2
    }

    "fail a Task with the classified exception" in {
      rateLimited = true
      try {
        runExit(service.createChatCompletion(messages, settings)) match {
          case Exit.Failure(cause) =>
            cause.failureOption.get shouldBe an[OpenAIScalaRateLimitException]
          case Exit.Success(value) =>
            fail(s"Expected a failure, got $value")
        }
      } finally rateLimited = false
    }

    "stream as a cold ZStream" in {
      chatRequests.set(0)
      val stream = service.createChatCompletionStreamedTyped(messages, settings)

      Thread.sleep(200)
      chatRequests.get shouldBe 0

      val texts = run(stream.collect { case ChatChunk.Text(text) => text }.runCollect)
      texts.toList shouldBe List("Hello")
      chatRequests.get shouldBe 1
    }

    "download a file as a byte ZStream - every byte value, a missing one as None" in {
      val content = run(service.retrieveFileContentAsSource("file-1")).get
      run(content.runCollect).toArray.toSeq shouldBe allBytes.toSeq

      run(service.retrieveFileContentAsSource("missing")) shouldBe None
    }

    "run the JSON helper as a Task" in {
      run(
        service.createChatCompletionWithJSON[City](
          messages,
          settings.copy(response_format_type = None)
        )
      ) shouldBe City("Oslo")
    }
  }

  "the layer" should {

    "build the view on acquisition and close the service with its scope" in {
      val closed = new AtomicInteger(0)
      val inner =
        OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
      val counting = new OpenAIChatCompletionService {
        override def createChatCompletion(
          messages: Seq[BaseMessage],
          settings: CreateChatCompletionSettings
        ): Future[ChatCompletionResponse] =
          inner.createChatCompletion(messages, settings)

        override def createChatToolCompletion(
          messages: Seq[BaseMessage],
          tools: Seq[ChatCompletionTool],
          responseToolChoice: Option[String],
          settings: CreateChatCompletionSettings
        ): Future[ChatToolCompletionResponse] =
          inner.createChatToolCompletion(messages, tools, responseToolChoice, settings)

        override def close(): Unit = {
          closed.incrementAndGet()
          ()
        }
      }

      val answer = run(
        ZIO
          .serviceWithZIO[OpenAIChatCompletionServiceZIO](
            _.createChatCompletion(messages, settings)
          )
          .provide(OpenAIChatCompletionServiceZIO.layer(() => counting))
      )

      answer.contentHead shouldBe "Hi"
      closed.get shouldBe 1
    }
  }
}
