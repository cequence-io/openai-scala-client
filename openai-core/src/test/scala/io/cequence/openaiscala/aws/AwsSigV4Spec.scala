package io.cequence.openaiscala.aws

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

/**
 * Known-answer tests against AWS's published `aws-sig-v4-test-suite` vectors. These pin the
 * signature bytes, so a refactor of [[AwsSigV4]] that changes canonicalization fails here
 * rather than in production with a 403.
 *
 * Not covered on purpose: the suite's `normalize-path` vectors. The signer does not implement
 * full RFC 3986 path normalization (it only percent-encodes `:`), which is sufficient for the
 * Bedrock and STS endpoints it is used against and is live-proven there.
 */
class AwsSigV4Spec extends AnyWordSpec with Matchers {

  // the credentials AWS publishes with the test suite - not real
  private val accessKey = "AKIDEXAMPLE"
  private val secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
  private val region = "us-east-1"
  private val service = "service"
  private val signedAt = Instant.parse("2015-08-30T12:36:00Z")

  private def sign(
    method: String,
    url: String,
    body: String = "",
    sessionToken: Option[String] = None
  ): Map[String, String] =
    AwsSigV4.signedHeaders(
      method = method,
      url = url,
      headers = Map.empty,
      body = body,
      accessKey = accessKey,
      secretKey = secretKey,
      region = region,
      service = service,
      sessionToken = sessionToken,
      now = signedAt
    )

  "AwsSigV4.signedHeaders" should {

    "match the published get-vanilla vector byte for byte" in {
      val headers = sign("GET", "https://example.amazonaws.com/")

      headers("X-Amz-Date") shouldBe "20150830T123600Z"
      headers("Host") shouldBe "example.amazonaws.com"
      headers("Authorization") shouldBe
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, " +
        "SignedHeaders=host;x-amz-date, " +
        "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31"
    }

    "sort query parameters canonically, not in the order given" in {
      // get-vanilla-query-order-key-case: Param2=value2 sorts after Param1=value1
      val a = sign("GET", "https://example.amazonaws.com/?Param2=value2&Param1=value1")
      val b = sign("GET", "https://example.amazonaws.com/?Param1=value1&Param2=value2")

      a("Authorization") shouldBe b("Authorization")
    }

    "order repeated query names by value, as SigV4 canonicalization requires" in {
      val a = sign("GET", "https://example.amazonaws.com/?tag=zeta&tag=alpha")
      val b = sign("GET", "https://example.amazonaws.com/?tag=alpha&tag=zeta")

      a("Authorization") shouldBe b("Authorization")
    }

    "include a session token in the signed headers when one is supplied" in {
      val headers = sign("GET", "https://example.amazonaws.com/", sessionToken = Some("tok"))

      headers("X-Amz-Security-Token") shouldBe "tok"
      headers("Authorization") should include(
        "SignedHeaders=host;x-amz-date;x-amz-security-token"
      )
      // and it must actually change the signature, i.e. it is signed rather than just sent
      headers("Authorization") should not be sign("GET", "https://example.amazonaws.com/")(
        "Authorization"
      )
    }

    "hash the body, so two different payloads sign differently" in {
      val empty = sign("POST", "https://example.amazonaws.com/", body = "")
      val withBody = sign("POST", "https://example.amazonaws.com/", body = """{"a":1}""")

      empty("Authorization") should not be withBody("Authorization")
    }

    "sign the method, so a GET and a POST of the same URL differ" in {
      sign("GET", "https://example.amazonaws.com/")("Authorization") should not be
        sign("POST", "https://example.amazonaws.com/")("Authorization")
    }

    "keep the caller's headers and add the AWS ones" in {
      val headers = AwsSigV4.signedHeaders(
        method = "POST",
        url = "https://example.amazonaws.com/path",
        headers = Map("Content-Type" -> "application/json"),
        body = "{}",
        accessKey = accessKey,
        secretKey = secretKey,
        region = region,
        service = service,
        now = signedAt
      )

      headers("Content-Type") shouldBe "application/json"
      // a caller-supplied header participates in the signature
      headers("Authorization") should include("content-type")
    }
  }

  "AwsSigV4.rfc3986Encode" should {

    "encode a space as %20 rather than +, and leave a tilde alone" in {
      AwsSigV4.rfc3986Encode("a b~c*d") shouldBe "a%20b~c%2Ad"
    }
  }
}
