package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * The status -> exception mapping and the error-body unpacking, the latter pinned to the
 * official Python SDK's `extract_message` rules.
 */
class HandleTypeSafeErrorCodesSpec extends AnyWordSpec with Matchers {

  import HandleTypeSafeErrorCodes.{extractMessage, toException}

  "toException" should {

    "map the statuses the API documents" in {
      toException(401, "") shouldBe an[OpenAIScalaUnauthorizedException]
      toException(403, "") shouldBe an[OpenAIScalaUnauthorizedException]
      toException(408, "") shouldBe an[OpenAIScalaClientTimeoutException]
      toException(429, "") shouldBe an[OpenAIScalaRateLimitException]
      toException(529, "") shouldBe an[OpenAIScalaEngineOverloadedException]
      toException(503, "") shouldBe an[OpenAIScalaEngineOverloadedException]
      toException(500, "") shouldBe an[OpenAIScalaServerErrorException]
      toException(502, "") shouldBe an[OpenAIScalaServerErrorException]
    }

    "leave validation and other client errors non-retryable" in {
      Seq(400, 404, 422).foreach { code =>
        val e = toException(code, "")
        e.getClass shouldBe classOf[OpenAIScalaClientException]
        Retryable(e) shouldBe false
      }
      Retryable(toException(401, "")) shouldBe false
    }

    "make rate limits, overloads and server errors retryable" in {
      Seq(408, 429, 503, 529, 500, 502).foreach { code =>
        withClue(s"$code: ") { Retryable(toException(code, "")) shouldBe true }
      }
    }

    "put the unpacked message after the code" in {
      toException(
        401,
        """{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}"""
      ).getMessage shouldBe
        "Code 401 : Cannot authenticate with the server. Please check your API key and try again."
    }
  }

  "extractMessage" should {

    "unpack the shapes the official SDK unpacks" in {
      extractMessage("""{"error":"boom"}""") shouldBe "boom"
      extractMessage("""{"error":{"message":"nested"}}""") shouldBe "nested"
      extractMessage("""{"message":"plain"}""") shouldBe "plain"
      extractMessage("""{"detail":"text detail"}""") shouldBe "text detail"
      extractMessage("""{"detail":{"message":"typesafe style"}}""") shouldBe "typesafe style"
      extractMessage("\"just a string\"") shouldBe "just a string"
    }

    "render a 422 validation list as `path: msg; path: msg`, dropping the `body` prefix" in {
      extractMessage(
        """{"detail":[
          |  {"loc":["body","state"],"msg":"Field required","type":"missing"},
          |  {"loc":["body","questions","q","criteria"],"msg":"Field required","type":"missing"}
          |]}""".stripMargin
      ) shouldBe "state: Field required; questions.q.criteria: Field required"

      extractMessage("""{"detail":[{"msg":"no location"}]}""") shouldBe "no location"
    }

    "fall back to the body as is" in {
      extractMessage("<html>502 Bad Gateway</html>") shouldBe "<html>502 Bad Gateway</html>"
      extractMessage("""{"unexpected":true}""") shouldBe """{"unexpected":true}"""
      extractMessage("""{"detail":[]}""") shouldBe """{"detail":[]}"""
    }
  }
}
