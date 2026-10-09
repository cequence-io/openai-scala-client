package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.gemini.domain.{ChatRole, Content, Part}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.service._
import io.cequence.openaiscala.{
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaUnauthorizedException,
  Retryable
}
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
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
 * A non-2xx answer to a STREAMED Gemini request, against a local server: since ws-client 1.1.1
 * the stream fails with a structured HTTP-status exception, which the Gemini service
 * classifies through `HandleGeminiErrorCodes`, and the OpenAI adapter repacks onto the
 * `OpenAIScala*` exceptions (plain and typed streams).
 */
class GeminiStreamedHttpErrorsWireSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("gemini-streamed-http-errors")
  private implicit val materializer: Materializer = Materializer(system)

  // status, body, content type
  @volatile private var reply: (Int, String, String) = (200, "{}", "application/json")
  @volatile private var lastPath: Option[String] = None

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val baseUrl = s"http://localhost:${server.getAddress.getPort}/v1beta/"
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())

  private lazy val service: GeminiService =
    new GeminiServiceImpl("k", None, Some(engine)) {
      override protected val site: SiteBinding = SiteBinding(
        baseUrl,
        WsRequestContext(authHeaders = Seq("x-goog-api-key" -> "k")),
        label = Some("gemini")
      )
    }
  private lazy val adapter = GeminiServiceFactory.asOpenAI(service)

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
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def error(
    code: Int,
    status: String,
    message: String
  ) = s"""{"error":{"code":$code,"message":"$message","status":"$status"}}"""

  private val rateLimited =
    (429, error(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota."))
  private val overloaded =
    (503, error(503, "UNAVAILABLE", "The model is overloaded. Please try again later."))
  private val badKey =
    (400, error(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."))

  private val model = "gemini-3.5-flash"

  private def failure[E <: Throwable: ClassTag](
    status: (Int, String)
  )(
    stream: => Source[_, _]
  ): E = {
    reply = (status._1, status._2, "application/json")
    intercept[E](Await.result(stream.runWith(Sink.seq), 20.seconds))
  }

  // a 200 SSE stream (CRLF-framed, as Gemini's): one chunk, then an in-band error frame
  private def inBandFailure[E <: Throwable: ClassTag](
    errorFrame: String
  )(
    stream: => Source[_, _]
  ): E = {
    reply = (
      200,
      "data: " +
        """{"candidates":[{"content":{"parts":[{"text":"Hel"}],"role":"model"},"index":0}],"usageMetadata":{"promptTokenCount":2,"totalTokenCount":3},"modelVersion":"gemini-3.5-flash"}""" +
        s"\r\n\r\ndata: $errorFrame\r\n\r\n",
      "text/event-stream"
    )
    intercept[E](Await.result(stream.runWith(Sink.seq), 20.seconds))
  }

  "a streamed Gemini generateContent" should {

    "fail with the classified Gemini exception" in {
      val contents = Seq(Content.textPart("hi", ChatRole.User))
      val settings = GenerateContentSettings(model)

      failure[GeminiScalaRateLimitException](rateLimited)(
        service.generateContentStreamed(contents, settings)
      ).getMessage should include("You exceeded your current quota.")
      lastPath shouldBe Some(s"/v1beta/models/$model:streamGenerateContent")

      failure[GeminiScalaEngineOverloadedException](overloaded)(
        service.generateContentStreamed(contents, settings)
      )

      failure[GeminiScalaUnauthorizedException](badKey)(
        service.generateContentStreamed(contents, settings)
      )
    }

    // 1.4.0 capped every stream at 1 MiB - an image model's picture arrives in one event
    "read an image part of 5 MiB in base64 whole" in {
      val image = "A" * (5 * 1024 * 1024)
      reply = (
        200,
        "data: " +
          s"""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"$image"}}],"role":"model"},"index":0}],"usageMetadata":{"promptTokenCount":2,"totalTokenCount":3},"modelVersion":"gemini-3-pro-image"}""" +
          "\r\n\r\n",
        "text/event-stream"
      )

      Await
        .result(
          service
            .generateContentStreamed(
              Seq(Content.textPart("draw a cat", ChatRole.User)),
              GenerateContentSettings("gemini-3-pro-image")
            )
            .runWith(Sink.seq),
          30.seconds
        )
        .flatMap(_.candidates.flatMap(_.content.parts))
        .collect { case Part.InlineData(_, data) => data.length } shouldBe Seq(image.length)
    }
  }

  "an in-band error frame of a 200 stream" should {

    "be classified by its code / status, natively and through the adapter" in {
      val unavailable =
        """{"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE"}}"""

      inBandFailure[GeminiScalaEngineOverloadedException](unavailable)(
        service.generateContentStreamed(
          Seq(Content.textPart("hi", ChatRole.User)),
          GenerateContentSettings(model)
        )
      )

      Retryable(
        inBandFailure[OpenAIScalaEngineOverloadedException](unavailable)(
          adapter.createChatCompletionStreamedTyped(
            Seq(UserMessage("hi")),
            CreateChatCompletionSettings(model)
          )
        )
      ) shouldBe true
    }
  }

  "the OpenAI adapter's streams" should {

    "repack the classified error onto the OpenAIScala exceptions" in {
      val messages = Seq(UserMessage("hi"))
      val settings = CreateChatCompletionSettings(model)

      val rateLimit = failure[OpenAIScalaRateLimitException](rateLimited)(
        adapter.createChatCompletionStreamed(messages, settings)
      )
      Retryable(rateLimit) shouldBe true
      rateLimit.getCause shouldBe a[GeminiScalaRateLimitException]

      Retryable(
        failure[OpenAIScalaEngineOverloadedException](overloaded)(
          adapter.createChatCompletionStreamedTyped(messages, settings)
        )
      ) shouldBe true

      failure[OpenAIScalaUnauthorizedException](badKey)(
        adapter.createChatToolCompletionStreamed(messages, Nil, None, settings)
      )
    }
  }
}
