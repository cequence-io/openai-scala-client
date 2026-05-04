package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.wsclient.service.ws.PlayJsonUtil
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

/**
 * Unit tests for Bedrock auth header construction — covers the three auth modes:
 *   - SigV4 with static IAM user credentials (accessKey + secretKey)
 *   - SigV4 with IAM role credentials (accessKey + secretKey + sessionToken)
 *   - Bearer token (Bedrock API key; SigV4 skipped entirely)
 */
class BedrockAuthSpec extends AnyWordSpec with Matchers {

  // Test harness exposing the production dispatch + SigV4 logic without pulling in the
  // heavy AnthropicBedrockServiceImpl (engine, materializer, etc.).
  private class TestAuth(connectionInfo: BedrockConnectionSettings) extends BedrockAuthHelper {
    private val serviceName = "bedrock"

    def signatureHeaders(
      method: String,
      url: String,
      headers: Seq[(String, String)],
      body: JsValue
    ): Seq[(String, String)] =
      connectionInfo.bearerToken match {
        case Some(token) =>
          headers :+ ("Authorization" -> s"Bearer $token")

        case None =>
          addAuthHeaders(
            method,
            url,
            headers.toMap,
            PlayJsonUtil.wsClientStringify(body),
            accessKey = connectionInfo.accessKey,
            secretKey = connectionInfo.secretKey,
            region = connectionInfo.region,
            service = serviceName,
            sessionToken = connectionInfo.sessionToken
          ).toSeq
      }
  }

  private val url = "https://bedrock-runtime.us-east-1.amazonaws.com/model/foo/invoke"
  private val body: JsValue = Json.obj("messages" -> Json.arr())
  private val baseHeaders: Seq[(String, String)] = Seq("Content-Type" -> "application/json")

  "createSignatureHeaders with a bearer token" should {

    "use Authorization: Bearer and skip SigV4 entirely" in {
      val settings = BedrockConnectionSettings(
        accessKey = "",
        secretKey = "",
        region = "us-east-1",
        bearerToken = Some("bedrock-api-key-abc123")
      )

      val result =
        new TestAuth(settings).signatureHeaders("POST", url, baseHeaders, body).toMap

      result("Authorization") shouldBe "Bearer bedrock-api-key-abc123"
      result should contain("Content-Type" -> "application/json")
      // SigV4 artefacts must NOT be present.
      result.keys should not contain "X-Amz-Date"
      result.keys should not contain "X-Amz-Security-Token"
      result("Authorization") should not startWith "AWS4-HMAC-SHA256"
    }
  }

  "createSignatureHeaders with static IAM user credentials (no session token)" should {

    "produce a SigV4 Authorization header and not set X-Amz-Security-Token" in {
      val settings = BedrockConnectionSettings(
        accessKey = "AKIAIOSFODNN7EXAMPLE",
        secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        region = "us-east-1"
      )

      val result =
        new TestAuth(settings).signatureHeaders("POST", url, baseHeaders, body).toMap

      result("Authorization") should startWith("AWS4-HMAC-SHA256")
      result("Authorization") should include("Credential=AKIAIOSFODNN7EXAMPLE")
      result.keys should contain("X-Amz-Date")
      result.keys should not contain "X-Amz-Security-Token"
      // Signed headers list (inside Authorization) must not mention the token either.
      result("Authorization") should not include "x-amz-security-token"
    }
  }

  "createSignatureHeaders with IAM role credentials (session token)" should {

    "include X-Amz-Security-Token in headers and in the signed headers list" in {
      val settings = BedrockConnectionSettings(
        accessKey = "ASIAIOSFODNN7EXAMPLE",
        secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        region = "us-east-1",
        sessionToken = Some("FwoGZXIvYXdzEJr//////////session-token-example")
      )

      val result =
        new TestAuth(settings).signatureHeaders("POST", url, baseHeaders, body).toMap

      result("X-Amz-Security-Token") shouldBe
        "FwoGZXIvYXdzEJr//////////session-token-example"
      result("Authorization") should startWith("AWS4-HMAC-SHA256")
      // SigV4 spec: when using temporary credentials, x-amz-security-token MUST be part
      // of the signed headers list so it participates in the signature.
      result("Authorization") should include("x-amz-security-token")
    }
  }
}
