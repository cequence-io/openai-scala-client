package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress

/**
 * Verifies that [[GeminiRawHttp]] sends the Gemini API key as the `x-goog-api-key` header
 * (never as a `key=...` URL query parameter) by spinning up a local HTTP server and inspecting
 * the request it receives, and that it reads/closes/disconnects responses correctly on both
 * the success and the error path without ever echoing the API key in an exception message.
 */
class GeminiRawHttpSpec extends AnyWordSpec with Matchers {

  private val ApiKey = "test-gemini-api-key-12345"

  private def withServer(handler: HttpHandler)(test: String => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    server.createContext("/", handler)
    server.start()
    try {
      val baseUrl = s"http://localhost:${server.getAddress.getPort}"
      test(baseUrl)
    } finally server.stop(0)
  }

  "GeminiRawHttp.openConnection/readResponse" should {

    "send the api key as a x-goog-api-key header, not as a URL query param, on success" in {
      @volatile var capturedUri: Option[String] = None
      @volatile var capturedApiKeyHeader: Option[String] = None

      withServer(new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          capturedUri = Some(exchange.getRequestURI.toString)
          capturedApiKeyHeader = Option(exchange.getRequestHeaders.getFirst("x-goog-api-key"))

          val bytes = """{"ok":true}""".getBytes("UTF-8")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }) { baseUrl =>
        val connection = GeminiRawHttp.openConnection(s"$baseUrl/v1beta/files", "GET", ApiKey)
        val body = GeminiRawHttp.readResponse(connection, "test operation")

        body shouldBe """{"ok":true}"""
        capturedApiKeyHeader shouldBe Some(ApiKey)
        capturedUri.get should not include "key="
      }
    }

    "keep response headers readable after readResponse has disconnected (upload-start flow)" in {
      withServer(new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getResponseHeaders.add("X-Goog-Upload-URL", "https://upload.example/abc")
          exchange.sendResponseHeaders(200, -1L)
          exchange.close()
        }
      }) { baseUrl =>
        val connection =
          GeminiRawHttp.openConnection(s"$baseUrl/upload/v1beta/files", "POST", ApiKey)
        val body = GeminiRawHttp.readResponse(connection, "start of the file upload")

        body shouldBe ""
        // GeminiServiceImpl.uploadFile reads the upload URL header AFTER readResponse, which
        // has already called disconnect() - the header must survive that
        connection.getHeaderField("X-Goog-Upload-URL") shouldBe "https://upload.example/abc"
      }
    }

    "throw on a >= 400 response with the status, op, error body, and no api key" in {
      val errorBody = """{"error":{"message":"not found"}}"""

      withServer(new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val bytes = errorBody.getBytes("UTF-8")
          exchange.sendResponseHeaders(404, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }) { baseUrl =>
        val connection =
          GeminiRawHttp.openConnection(s"$baseUrl/v1beta/files/abc", "GET", ApiKey)

        val ex = intercept[OpenAIScalaClientException] {
          GeminiRawHttp.readResponse(connection, "download of the file 'abc'")
        }

        ex.getMessage should include("status 404")
        ex.getMessage should include("download of the file 'abc'")
        ex.getMessage should include(errorBody)
        ex.getMessage should not include ApiKey
      }
    }

    "preserve an existing query string and still not add key= to the URL" in {
      @volatile var capturedUri: Option[String] = None

      withServer(new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          capturedUri = Some(exchange.getRequestURI.toString)

          val bytes = "downloaded-content".getBytes("UTF-8")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }) { baseUrl =>
        val url = s"$baseUrl/download/v1beta/files/abc:download?alt=media"
        val connection = GeminiRawHttp.openConnection(url, "GET", ApiKey)
        val body = GeminiRawHttp.readResponse(connection, "download")

        body shouldBe "downloaded-content"
        capturedUri.get should include("alt=media")
        capturedUri.get should not include "key="
      }
    }
  }

  // NOTE: GeminiServiceImpl's `site` val (which now uses
  // WsRequestContext(authHeaders = Seq("x-goog-api-key" -> apiKey)) instead of extraParams for
  // the main JSON-engine path) is `protected`, so it can't be asserted on directly from a test
  // without weakening its visibility - which we intentionally avoid. The raw-path coverage
  // above is the testable surface for the api-key-in-header behavior.
}
