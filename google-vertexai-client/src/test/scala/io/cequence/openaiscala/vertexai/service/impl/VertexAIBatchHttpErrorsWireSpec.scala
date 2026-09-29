package io.cequence.openaiscala.vertexai.service.impl

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala._
import io.cequence.openaiscala.vertexai.service.VertexAIBatchPredictionService
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

/**
 * The Vertex AI batch-prediction REST calls against a local server: Google's error bodies are
 * classified by their canonical status (`Retryable` for 429 / 503 / 5xx), a non-Google body by
 * its HTTP code, and a transport timeout - through the site's error recovery - becomes an
 * `OpenAIScala*` exception too (the unknown-host mapping is unit-tested in
 * `VertexAIErrorsSpec`, without a live DNS lookup).
 */
class VertexAIBatchHttpErrorsWireSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  // status, body, delay before answering
  @volatile private var reply: (Int, String, Long) = (200, "{}", 0L)

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/v1/"
  // default timeouts for the status tests; a short one only for the timeout test
  private val engine = WSClientEngineRegistry(TransportSettings())
  private val shortTimeoutEngine = WSClientEngineRegistry(
    TransportSettings(timeouts = Timeouts(requestTimeout = Some(500), readTimeout = Some(500)))
  )

  private def batchService(
    url: => String,
    serviceEngine: WSClientEngine = engine
  ): VertexAIBatchPredictionService =
    new VertexAIBatchPredictionServiceImpl("p", "us-central1", None, Some(serviceEngine)) {
      // the production binding (incl. its error recovery), pointed at the local server with a
      // static token instead of Application Default Credentials
      override protected val site: SiteBinding =
        VertexAIBatchPredictionServiceImpl
          .siteBinding("p", "us-central1")
          .copy(
            coreUrl = url,
            requestContextFun =
              Some(() => WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer t")))
          )
    }

  private lazy val service = batchService(coreUrl)
  private lazy val shortTimeoutService = batchService(coreUrl, shortTimeoutEngine)

  override protected def beforeAll(): Unit = {
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool())
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val (status, body, delay) = reply
          if (delay > 0) Thread.sleep(delay)
          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          try {
            exchange.sendResponseHeaders(status, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
          } catch { case _: java.io.IOException => () } // the client gave up (timeout test)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    shortTimeoutService.close()
    engine.close()
    shortTimeoutEngine.close()
    server.stop(0)
  }

  private def error(
    code: Int,
    status: String,
    message: String
  ) = s"""{"error":{"code":$code,"message":"$message","status":"$status"}}"""

  private def failure[E <: Throwable: ClassTag](
    status: Int,
    body: String,
    delay: Long = 0L
  )(
    call: => Future[_]
  ): E = {
    reply = (status, body, delay)
    intercept[E](Await.result(call, 20.seconds))
  }

  "the batch-prediction calls" should {

    "classify Google's error bodies by their canonical status" in {
      val rateLimit = failure[OpenAIScalaRateLimitException](
        429,
        error(429, "RESOURCE_EXHAUSTED", "Quota exceeded for aiplatform.googleapis.com.")
      )(service.getBatchPredictionJob("123"))
      rateLimit.getMessage shouldBe "Code 429 : Quota exceeded for aiplatform.googleapis.com."
      Retryable(rateLimit) shouldBe true

      Retryable(
        failure[OpenAIScalaEngineOverloadedException](
          503,
          error(503, "UNAVAILABLE", "The service is currently unavailable.")
        )(service.listBatchPredictionJobs())
      ) shouldBe true

      failure[OpenAIScalaUnauthorizedException](
        401,
        error(401, "UNAUTHENTICATED", "Request had invalid authentication credentials.")
      )(service.cancelBatchPredictionJob("123"))

      val notFound = failure[OpenAIScalaClientException](
        404,
        error(404, "NOT_FOUND", "The BatchPredictionJob does not exist.")
      )(service.deleteBatchPredictionJob("123"))
      notFound.getClass shouldBe classOf[OpenAIScalaClientException]
      Retryable(notFound) shouldBe false
    }

    "classify a non-Google (gateway) body by its HTTP code" in {
      Retryable(
        failure[OpenAIScalaServerErrorException](502, "<html>Bad Gateway</html>")(
          service.getBatchPredictionJob("123")
        )
      ) shouldBe true
    }

    "turn a transport timeout into OpenAIScalaClientTimeoutException" in {
      Retryable(
        failure[OpenAIScalaClientTimeoutException](200, "{}", delay = 1500)(
          shortTimeoutService.getBatchPredictionJob("123")
        )
      ) shouldBe true
    }
  }
}
