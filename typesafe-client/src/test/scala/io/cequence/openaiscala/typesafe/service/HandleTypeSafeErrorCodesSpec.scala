package io.cequence.openaiscala.typesafe.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * The status + body -> exception classification, pinned to the bodies collected against the
 * live API on 2026-09-17, and the error-body unpacking (the official SDK's `extract_message`
 * rules).
 */
class HandleTypeSafeErrorCodesSpec extends AnyWordSpec with Matchers {

  import HandleTypeSafeErrorCodes.{errorType, extractMessage, toException}

  private val authError =
    """{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}"""
  private val noKey =
    """{"detail":{"error_type":"authentication_error","message":"Must supply an API key! Check your request and try again."}}"""
  private val tokenLimit = """{"detail":{"error_type":"max_tokens_exceeded"}}"""
  private val unknownModel =
    """{"detail":{"error_type":"api_usage_error","message":"Unknown model: jev-nope"}}"""
  private val invalidJson =
    """{"detail":{"error_type":"api_usage_error","message":"Request contains invalid JSON."}}"""
  private val notEnabled =
    """{"detail":{"error_type":"api_usage_error","message":"Bounding-box questions are not enabled for your organization."}}"""
  private val tooManyChoices =
    """{"detail":"Too many choices. Must have at most 255 choices."}"""
  private val validation =
    """{"detail":[
      |  {"type":"missing","loc":["body","state"],"msg":"Field required","input":{}},
      |  {"type":"too_short","loc":["body","questions"],"msg":"Dictionary should have at least 1 item after validation, not 0","input":{},"ctx":{"min_length":1}}
      |]}""".stripMargin
  private val notFound = """{"detail":"Not Found"}"""

  "toException" should {

    "classify the bodies the live API sends" in {
      toException(401, authError) shouldBe a[TypeSafeScalaUnauthorizedException]
      toException(403, noKey) shouldBe a[TypeSafeScalaUnauthorizedException]
      toException(400, tokenLimit) shouldBe a[TypeSafeScalaTokenCountExceededException]
      toException(400, unknownModel) shouldBe a[TypeSafeScalaApiUsageException]
      toException(400, invalidJson) shouldBe a[TypeSafeScalaApiUsageException]
      toException(400, notEnabled) shouldBe a[TypeSafeScalaApiUsageException]
      toException(400, tooManyChoices) shouldBe a[TypeSafeScalaInvalidRequestException]
      toException(422, validation) shouldBe a[TypeSafeScalaInvalidRequestException]
      toException(404, notFound) shouldBe a[TypeSafeScalaNotFoundException]
      toException(405, """{"detail":"Method Not Allowed"}""") shouldBe
        a[TypeSafeScalaNotFoundException]
      toException(408, "") shouldBe a[TypeSafeScalaClientTimeoutException]
      toException(429, """{"detail":"Rate limit exceeded"}""") shouldBe
        a[TypeSafeScalaRateLimitException]
      toException(529, """{"detail":"Overloaded"}""") shouldBe
        a[TypeSafeScalaEngineOverloadedException]
      toException(503, "") shouldBe a[TypeSafeScalaEngineOverloadedException]
      toException(500, "") shouldBe a[TypeSafeScalaServerErrorException]
      toException(502, "<html>502 Bad Gateway</html>") shouldBe
        a[TypeSafeScalaServerErrorException]
      toException(418, "teapot").getClass shouldBe classOf[TypeSafeScalaClientException]
    }

    "carry the code, the error type and the request id, and put them in the message" in {
      val e = toException(400, unknownModel, requestId = Some("req_1"))
      e shouldBe a[io.cequence.openaiscala.ProviderErrorDetails]
      e.httpCode shouldBe Some(400)
      e.errorType shouldBe Some("api_usage_error")
      e.requestId shouldBe Some("req_1")
      e.getMessage shouldBe "Code 400 : Unknown model: jev-nope [request req_1]"

      val limit = toException(400, tokenLimit)
      limit.errorType shouldBe Some("max_tokens_exceeded")
      limit.requestId shouldBe None
      limit.getMessage shouldBe "Code 400 : max_tokens_exceeded"

      toException(401, authError).getMessage shouldBe
        "Code 401 : Cannot authenticate with the server. Please check your API key and try again."
    }

    "expose a 422's violations one by one, paths without the body prefix" in {
      val e = toException(422, validation).asInstanceOf[TypeSafeScalaInvalidRequestException]
      e.violations shouldBe Seq(
        TypeSafeViolation("state", "Field required"),
        TypeSafeViolation(
          "questions",
          "Dictionary should have at least 1 item after validation, not 0"
        )
      )
      e.getMessage should include(
        "state: Field required; questions: Dictionary should have at least 1 item"
      )
      toException(400, tooManyChoices)
        .asInstanceOf[TypeSafeScalaInvalidRequestException]
        .violations shouldBe empty
    }

    "mark rate limits, overloads, server errors and timeouts retryable, nothing else" in {
      Seq(408, 429, 500, 502, 503, 529).foreach { code =>
        withClue(s"$code: ") { TypeSafeRetryable(toException(code, "")) shouldBe true }
      }
      Seq(
        toException(400, tokenLimit),
        toException(400, unknownModel),
        toException(400, tooManyChoices),
        toException(401, authError),
        toException(404, notFound),
        toException(422, validation)
      ).foreach { e =>
        withClue(s"${e.getClass.getSimpleName}: ") { TypeSafeRetryable(e) shouldBe false }
      }
      // the extractor form, on a plain Throwable
      (new RuntimeException("x") match {
        case TypeSafeRetryable(_) => true
        case _                    => false
      }) shouldBe false
    }
  }

  "extractMessage / errorType" should {

    "unpack the shapes the official SDK unpacks" in {
      extractMessage("""{"error":"boom"}""") shouldBe "boom"
      extractMessage("""{"error":{"message":"nested"}}""") shouldBe "nested"
      extractMessage("""{"message":"plain"}""") shouldBe "plain"
      extractMessage("""{"detail":"text detail"}""") shouldBe "text detail"
      extractMessage("""{"detail":{"message":"typesafe style"}}""") shouldBe "typesafe style"
      extractMessage("\"just a string\"") shouldBe "just a string"
    }

    "render a validation list, fall back to the error type, then to the body" in {
      extractMessage(
        """{"detail":[{"loc":["body","questions","q","criteria"],"msg":"Field required"},{"msg":"no location"}]}"""
      ) shouldBe "questions.q.criteria: Field required; no location"
      extractMessage(tokenLimit) shouldBe "max_tokens_exceeded"
      extractMessage("<html>502 Bad Gateway</html>") shouldBe "<html>502 Bad Gateway</html>"
      extractMessage("""{"unexpected":true}""") shouldBe """{"unexpected":true}"""
      extractMessage("""{"detail":[]}""") shouldBe """{"detail":[]}"""

      errorType(tokenLimit) shouldBe Some("max_tokens_exceeded")
      errorType(unknownModel) shouldBe Some("api_usage_error")
      errorType(tooManyChoices) shouldBe None
      errorType("not json") shouldBe None
    }
  }
}
