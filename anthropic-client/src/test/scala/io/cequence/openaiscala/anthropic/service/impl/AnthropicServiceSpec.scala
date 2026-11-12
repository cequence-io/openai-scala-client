package io.cequence.openaiscala.anthropic.service.impl

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service._
import io.cequence.wsclient.domain.WsRequestContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag

/**
 * A non-2xx answer to a plain (non-streamed) `createMessage`, against a local server: the
 * service classifies it by status into the Anthropic exception hierarchy. The streamed
 * counterpart is `AnthropicStreamedHttpErrorsWireSpec`.
 */
class AnthropicServiceSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  @volatile private var reply: (Int, String) = (200, "{}")

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)

  private lazy val service = AnthropicServiceFactory.customInstance(
    s"http://localhost:${server.getAddress.getPort}/",
    WsRequestContext(authHeaders = Seq("x-api-key" -> "k"))
  )

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val (status, body) = reply
          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    server.stop(0)
    ()
  }

  private val messages = Seq(UserMessage("Hello"))
  private val settings = AnthropicCreateMessageSettings("claude-haiku-5-5", max_tokens = 100)

  private def failure[E <: Throwable: ClassTag](
    status: Int,
    kind: String,
    message: String
  ): E = {
    reply = (status, s"""{"type":"error","error":{"type":"$kind","message":"$message"}}""")
    intercept[E](Await.result(service.createMessage(messages, settings), 20.seconds))
  }

  "Anthropic service createMessage" should {

    "fail with an unauthorized exception on 401" in {
      failure[AnthropicScalaUnauthorizedException](
        401,
        "authentication_error",
        "invalid x-api-key"
      )
    }

    "fail with an unauthorized exception on 403" in {
      failure[AnthropicScalaUnauthorizedException](403, "permission_error", "not permitted")
    }

    "fail with a not-found exception on 404" in {
      failure[AnthropicScalaNotFoundException](404, "not_found_error", "model: nope")
    }

    "fail with a rate-limit exception on 429" in {
      failure[AnthropicScalaRateLimitException](429, "rate_limit_error", "rate limited")
    }

    "fail with a server-error exception on 500" in {
      failure[AnthropicScalaServerErrorException](500, "api_error", "internal error")
    }

    "fail with an engine-overloaded exception on 529" in {
      failure[AnthropicScalaEngineOverloadedException](529, "overloaded_error", "Overloaded")
    }

    "fail with a client exception on 400" in {
      failure[AnthropicScalaClientException](400, "invalid_request_error", "bad request")
    }

    "fail with a client exception on a status without a rule of its own" in {
      failure[AnthropicScalaClientException](418, "teapot", "no")
    }
  }
}
