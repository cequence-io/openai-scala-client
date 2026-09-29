package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala._
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.responsesapi.Inputs
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateCompletionSettings
}
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
import scala.reflect.ClassTag

/**
 * A non-2xx answer to a STREAMED request, against a local server: since ws-client 1.1.1 the
 * stream fails with a structured HTTP-status exception, which the streamed services classify
 * through `HandleOpenAIErrorCodes` - the same exceptions (and `Retryable` verdicts) as the
 * non-streamed calls.
 */
class StreamedHttpErrorsWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("streamed-http-errors-wire")
  private implicit val materializer: Materializer = Materializer(system)

  // status, body, content type
  @volatile private var reply: (Int, String, String) = (200, "{}", "application/json")

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val fullStreamed =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  private lazy val chatOnlyStreamed =
    OpenAIChatCompletionServiceFactory.withStreaming.withEngine(engine, coreUrl, context)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val (status, body, contentType) = reply
          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", contentType)
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    fullStreamed.close()
    chatOnlyStreamed.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def error(
    message: String,
    kind: String
  ) = s"""{"error":{"message":"$message","type":"$kind","code":null}}"""

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gpt-5.4")

  private def failure[E <: Throwable: ClassTag](
    status: Int,
    body: String
  )(
    stream: => Source[_, _]
  ): E = {
    reply = (status, body, "application/json")
    intercept[E](Await.result(stream.runWith(Sink.seq), 20.seconds))
  }

  // a 200 SSE stream: one chunk, then an in-band error frame
  private def inBandFailure[E <: Throwable: ClassTag](
    chunk: String,
    errorFrame: String
  )(
    stream: => Source[_, _]
  ): E = {
    reply = (200, s"data: $chunk\n\ndata: $errorFrame\n\n", "text/event-stream")
    intercept[E](Await.result(stream.runWith(Sink.seq), 20.seconds))
  }

  private val chatChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}"""

  "a streamed chat completion" should {

    "fail with the classified OpenAI exception, on the full and the chat-only service" in {
      Seq(fullStreamed, chatOnlyStreamed).foreach { service =>
        val rateLimit = failure[OpenAIScalaRateLimitException](
          429,
          error("Rate limit reached", "requests")
        )(service.createChatCompletionStreamed(messages, settings))
        rateLimit.getMessage should include("Rate limit reached")
        Retryable(rateLimit) shouldBe true

        failure[OpenAIScalaUnauthorizedException](
          401,
          error("Incorrect API key provided", "invalid_request_error")
        )(service.createChatCompletionStreamed(messages, settings))

        failure[OpenAIScalaTokenCountExceededException](
          400,
          error("This model's maximum context length is 8192 tokens.", "invalid_request_error")
        )(service.createChatCompletionStreamed(messages, settings))

        Retryable(
          failure[OpenAIScalaEngineOverloadedException](503, "Service Unavailable")(
            service.createChatCompletionStreamed(messages, settings)
          )
        ) shouldBe true
      }
    }

    "fail the typed stream the same way" in {
      failure[OpenAIScalaServerErrorException](502, "Bad Gateway")(
        fullStreamed.createChatCompletionStreamedTyped(messages, settings)
      )
    }
  }

  "an in-band error frame of a 200 stream" should {

    "be classified by its type, like the HTTP status it stands for" in {
      Retryable(
        inBandFailure[OpenAIScalaServerErrorException](
          chatChunk,
          """{"error":{"message":"The server had an error while processing your request.","type":"server_error","param":null,"code":null}}"""
        )(chatOnlyStreamed.createChatCompletionStreamed(messages, settings))
      ) shouldBe true

      inBandFailure[OpenAIScalaRateLimitException](
        chatChunk,
        """{"error":{"message":"Rate limit reached","type":"requests","code":"rate_limit_exceeded"}}"""
      )(fullStreamed.createChatCompletionStreamedTyped(messages, settings))
    }

    "stay a plain OpenAIScalaClientException when it names no status" in {
      inBandFailure[OpenAIScalaClientException](chatChunk, """{"error":{"message":"odd"}}""")(
        fullStreamed.createChatCompletionStreamed(messages, settings)
      ).getClass shouldBe classOf[OpenAIScalaClientException]
    }
  }

  "the streamed completions and Responses API" should {

    "fail with the classified OpenAI exception" in {
      failure[OpenAIScalaRateLimitException](429, error("Rate limit reached", "requests"))(
        fullStreamed.createCompletionStreamed(
          "hi",
          CreateCompletionSettings("gpt-3.5-turbo-instruct")
        )
      )

      failure[OpenAIScalaUnauthorizedException](
        401,
        error("Incorrect API key provided", "invalid_request_error")
      )(fullStreamed.createModelResponseStreamed(Inputs.Text("hi")))
    }
  }
}
