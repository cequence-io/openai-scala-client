package io.cequence.openaiscala.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * [[InBandStreamErrors.httpStatus]]: the HTTP status an `{"error": ...}` stream frame stands
 * for, in each provider's shape.
 */
class InBandStreamErrorsSpec extends AnyWordSpec with Matchers {

  private def status(error: String) = InBandStreamErrors.httpStatus(Json.parse(error))

  "InBandStreamErrors.httpStatus" should {

    "read a numeric code, as a number or a string (Google, Perplexity)" in {
      status("""{"code":429,"message":"Rate limit","status":"RESOURCE_EXHAUSTED"}""") shouldBe
        Some(429)
      status(
        """{"code":"401","message":"Invalid API key","type":"invalid_api_key"}"""
      ) shouldBe
        Some(401)
    }

    "fall back to a Google canonical status" in {
      status("""{"message":"overloaded","status":"UNAVAILABLE"}""") shouldBe Some(503)
      status("""{"message":"quota","status":"RESOURCE_EXHAUSTED"}""") shouldBe Some(429)
      status("""{"message":"deadline","status":"DEADLINE_EXCEEDED"}""") shouldBe Some(504)
    }

    "fall back to an OpenAI / Anthropic error type" in {
      status(
        """{"message":"The server had an error","type":"server_error","code":null}"""
      ) shouldBe Some(500)
      status(
        """{"message":"Rate limit","type":"requests","code":"rate_limit_exceeded"}"""
      ) shouldBe
        Some(429)
      status("""{"type":"overloaded_error","message":"Overloaded"}""") shouldBe Some(529)
      status("""{"type":"api_error","message":"Internal"}""") shouldBe Some(500)
    }

    "ignore a code that is not an HTTP error status, and name none for an unknown frame" in {
      status("""{"code":200,"type":"server_error"}""") shouldBe Some(500)
      status("""{"code":"context_length_exceeded","message":"too long"}""") shouldBe None
      status("""{"message":"odd"}""") shouldBe None
    }
  }
}
