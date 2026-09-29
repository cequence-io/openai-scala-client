package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{UserMessage => OpenAIUserMessage}
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaTokenCountExceededException,
  Retryable
}
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}
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
 * A non-2xx answer to a STREAMED Anthropic request, against a local server: since ws-client
 * 1.1.1 the stream fails with a structured HTTP-status exception, which the Anthropic services
 * (direct API and Bedrock) classify through `HandleAnthropicErrorCodes`, and the OpenAI
 * adapter repacks onto the `OpenAIScala*` exceptions.
 */
class AnthropicStreamedHttpErrorsWireSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("anthropic-streamed-http-errors")
  private implicit val materializer: Materializer = Materializer(system)

  // status, body, content type
  @volatile private var reply: (Int, String, String) = (200, "{}", "application/json")
  @volatile private var lastPath: Option[String] = None

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val bedrockEngine = StreamedEngineRegistry.outputStreamed(TransportSettings())

  private lazy val service = AnthropicServiceFactory.customInstance(
    coreUrl,
    WsRequestContext(authHeaders = Seq("x-api-key" -> "k"))
  )
  private lazy val adapter = AnthropicServiceFactory.asOpenAI(service)

  // the Bedrock Invoke service (bearer auth, no SigV4) pointed at the local server
  private lazy val bedrock: AnthropicService = new AnthropicBedrockServiceImpl {
    override implicit val ec: ExecutionContext = ExecutionContext.global
    override val connectionInfo: BedrockConnectionSettings =
      BedrockConnectionSettings("", "", "us-east-1", bearerToken = Some("t"))
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
      bedrockEngine
    override protected def ownsEngine: Boolean = false
    override protected val site: SiteBinding =
      SiteBinding(coreUrl, label = Some("anthropic-bedrock"))
  }

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          lastPath = Some(exchange.getRequestURI.getPath)
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
    adapter.close()
    bedrock.close()
    bedrockEngine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def error(
    kind: String,
    message: String
  ) = s"""{"type":"error","error":{"type":"$kind","message":"$message"}}"""

  private val rateLimited = (429, error("rate_limit_error", "Number of requests exceeded"))
  private val overloaded = (529, error("overloaded_error", "Overloaded"))
  private val tooLong =
    (400, error("invalid_request_error", "prompt is too long: 250000 tokens"))

  private val messages = Seq(UserMessage("hi"))
  private val settings = AnthropicCreateMessageSettings("claude-sonnet-5", max_tokens = 100)

  private def failure[E <: Throwable: ClassTag](
    status: (Int, String)
  )(
    stream: => Source[_, _]
  ): E = {
    reply = (status._1, status._2, "application/json")
    intercept[E](Await.result(stream.runWith(Sink.seq), 20.seconds))
  }

  // a 200 message stream that starts, then reports an error event mid-stream
  private def inBandFailure[E <: Throwable: ClassTag](
    errorFrame: String
  )(
    stream: => Source[_, _]
  ): E = {
    reply = (
      200,
      "event: message_start\ndata: " +
        """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-sonnet-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":5,"output_tokens":1}}}""" +
        s"\n\nevent: error\ndata: $errorFrame\n\n",
      "text/event-stream"
    )
    intercept[E](Await.result(stream.runWith(Sink.seq), 20.seconds))
  }

  "a streamed Anthropic message" should {

    "fail with the classified Anthropic exception" in {
      failure[AnthropicScalaRateLimitException](rateLimited)(
        service.createMessageStreamed(messages, settings)
      ).getMessage should include("Number of requests exceeded")
      lastPath shouldBe Some("/messages")

      failure[AnthropicScalaEngineOverloadedException](overloaded)(
        service.createMessageStreamedEvents(messages, settings)
      )

      failure[AnthropicScalaTokenCountExceededException](tooLong)(
        service.createMessageStreamed(messages, settings)
      )

      failure[AnthropicScalaUnauthorizedException](
        (401, error("authentication_error", "invalid x-api-key"))
      )(service.createMessageStreamed(messages, settings))
    }

    "classify an in-band error event by its type" in {
      Retryable(
        toOpenAIException(
          inBandFailure[AnthropicScalaEngineOverloadedException](
            """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
          )(service.createMessageStreamedEvents(messages, settings))
        ).asInstanceOf[OpenAIScalaClientException]
      ) shouldBe true

      inBandFailure[AnthropicScalaRateLimitException](
        """{"type":"error","error":{"type":"rate_limit_error","message":"Rate limited"}}"""
      )(service.createMessageStreamed(messages, settings))
    }

    "fail the Managed Agents session event stream the same way" in {
      failure[AnthropicScalaRateLimitException](rateLimited)(
        service.streamSessionEvents("sesn_1")
      )
      lastPath shouldBe Some("/sessions/sesn_1/events/stream")
    }

    "fail the streamed batch results the same way" in {
      failure[AnthropicScalaNotFoundException](
        (404, error("not_found_error", "batch not found"))
      )(service.streamMessageBatchResults("msgbatch_1"))
    }

    "fail on Bedrock (invoke-with-response-stream) the same way" in {
      failure[AnthropicScalaRateLimitException](
        (429, """{"message":"Too many requests, please wait before trying again."}""")
      )(bedrock.createMessageStreamed(messages, settings))
      lastPath.exists(_.endsWith("/invoke-with-response-stream")) shouldBe true

      failure[AnthropicScalaTokenCountExceededException](
        (400, """{"message":"Input is too long for requested model."}""")
      )(bedrock.createMessageStreamed(messages, settings))
    }
  }

  "the OpenAI adapter's streams" should {

    "repack the classified error onto the OpenAIScala exceptions" in {
      val openAIMessages = Seq(OpenAIUserMessage("hi"))
      val openAISettings = CreateChatCompletionSettings("claude-sonnet-5")

      val rateLimit = failure[OpenAIScalaRateLimitException](rateLimited)(
        adapter.createChatCompletionStreamed(openAIMessages, openAISettings)
      )
      Retryable(rateLimit) shouldBe true
      rateLimit.getCause shouldBe an[AnthropicScalaRateLimitException]

      failure[OpenAIScalaTokenCountExceededException](tooLong)(
        adapter.createChatCompletionStreamedTyped(openAIMessages, openAISettings)
      )

      Retryable(
        inBandFailure[OpenAIScalaEngineOverloadedException](
          """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
        )(adapter.createChatCompletionStreamed(openAIMessages, openAISettings))
      ) shouldBe true
    }
  }
}
