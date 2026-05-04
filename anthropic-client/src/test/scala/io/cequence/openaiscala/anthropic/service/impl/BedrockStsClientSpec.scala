package io.cequence.openaiscala.anthropic.service.impl

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import scala.io.Source

/**
 * Verifies the SigV4-signed STS:GetSessionToken call by spinning up a local HTTP server that
 * mimics the STS XML response. We can't hit real STS without real long-lived credentials, but
 * the local mock confirms (a) the request is signed and the body is what STS expects, and (b)
 * the XML response is parsed correctly.
 */
class BedrockStsClientSpec extends AnyWordSpec with Matchers {

  private val FakeAccessKey = "AKIAIOSFODNN7EXAMPLE"
  private val FakeSecretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"

  private val MintedAccessKey = "ASIATESTKEY"
  private val MintedSecretKey = "ASIATestSecret"
  private val MintedSessionToken = "FwoGZXIvYXdzEPr//////////minted-token"
  private val MintedExpiration = "2026-12-31T23:59:59Z"

  private val ResponseXml =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<GetSessionTokenResponse>
       |  <GetSessionTokenResult>
       |    <Credentials>
       |      <AccessKeyId>$MintedAccessKey</AccessKeyId>
       |      <SecretAccessKey>$MintedSecretKey</SecretAccessKey>
       |      <SessionToken>$MintedSessionToken</SessionToken>
       |      <Expiration>$MintedExpiration</Expiration>
       |    </Credentials>
       |  </GetSessionTokenResult>
       |  <ResponseMetadata>
       |    <RequestId>test-request-id</RequestId>
       |  </ResponseMetadata>
       |</GetSessionTokenResponse>""".stripMargin

  "BedrockStsClient.getSessionToken" should {

    "sign the request with SigV4 and parse the STS XML response" in {
      val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)

      // Capture the request shape so we can assert SigV4 signing happened.
      @volatile var capturedAuth: Option[String] = None
      @volatile var capturedAmzDate: Option[String] = None
      @volatile var capturedBody: Option[String] = None
      @volatile var capturedContentType: Option[String] = None

      server.createContext(
        "/",
        new HttpHandler {
          override def handle(exchange: HttpExchange): Unit = {
            capturedAuth = Option(exchange.getRequestHeaders.getFirst("Authorization"))
            capturedAmzDate = Option(exchange.getRequestHeaders.getFirst("X-Amz-Date"))
            capturedContentType = Option(exchange.getRequestHeaders.getFirst("Content-Type"))
            val src = Source.fromInputStream(exchange.getRequestBody)
            capturedBody =
              try Some(src.mkString)
              finally src.close()

            val bytes = ResponseXml.getBytes("UTF-8")
            exchange.sendResponseHeaders(200, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
            exchange.close()
          }
        }
      )

      server.start()
      try {
        val endpoint = s"http://localhost:${server.getAddress.getPort}/"

        val result = BedrockStsClient.getSessionToken(
          accessKey = FakeAccessKey,
          secretKey = FakeSecretKey,
          durationSeconds = 1800,
          endpoint = endpoint
        )

        result.accessKeyId shouldBe MintedAccessKey
        result.secretAccessKey shouldBe MintedSecretKey
        result.sessionToken shouldBe MintedSessionToken
        result.expiration shouldBe MintedExpiration

        // SigV4 artefacts:
        capturedAuth.get should startWith("AWS4-HMAC-SHA256")
        capturedAuth.get should include(s"Credential=$FakeAccessKey")
        capturedAuth.get should include("us-east-1/sts/aws4_request")
        capturedAmzDate.get should fullyMatch regex """\d{8}T\d{6}Z"""

        // Request body and content type expected by STS GetSessionToken:
        capturedContentType.get shouldBe "application/x-www-form-urlencoded"
        capturedBody.get shouldBe
          "Action=GetSessionToken&Version=2011-06-15&DurationSeconds=1800"
      } finally server.stop(0)
    }

    "throw with the response body on a non-2xx STS error" in {
      val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
      server.createContext(
        "/",
        new HttpHandler {
          override def handle(exchange: HttpExchange): Unit = {
            val errBody =
              """<ErrorResponse><Error><Code>InvalidClientTokenId</Code></Error></ErrorResponse>"""
            val bytes = errBody.getBytes("UTF-8")
            exchange.sendResponseHeaders(403, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
            exchange.close()
          }
        }
      )

      server.start()
      try {
        val endpoint = s"http://localhost:${server.getAddress.getPort}/"

        val ex = intercept[RuntimeException] {
          BedrockStsClient.getSessionToken(
            accessKey = FakeAccessKey,
            secretKey = FakeSecretKey,
            endpoint = endpoint
          )
        }
        ex.getMessage should include("status 403")
        ex.getMessage should include("InvalidClientTokenId")
      } finally server.stop(0)
    }
  }
}
