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

  "Liquid AI's error bodies (the same System One API, OpenAI-style errors)" should {

    // recorded from https://api.liquid.ai/decisions/v1/systemone on 2026-09-30, d1's launch day
    "be classified by status, carrying their code or type" in {
      val unavailable = toException(
        429,
        """{"error":{"message":"The model `d1:free` is temporarily unable to accept requests. Please try again shortly.","type":"rate_limit_error","param":null,"code":"model_unavailable"}}"""
      )
      unavailable shouldBe a[TypeSafeScalaRateLimitException]
      unavailable.errorType shouldBe Some("model_unavailable")
      unavailable.getMessage should include("temporarily unable to accept requests")

      val backendsDown = toException(
        502,
        """{"error":{"message":"All upstream backends failed for this model.","type":"api_error","param":null,"code":null}}"""
      )
      backendsDown shouldBe a[TypeSafeScalaServerErrorException]
      backendsDown.errorType shouldBe Some("api_error")
      backendsDown.getMessage should include("All upstream backends failed")
    }
  }

  "Perplexity's Decisions API error bodies (OpenAI-style, the code a mere status)" should {

    // recorded from https://api.perplexity.ai/v1/decisions on 2026-10-02
    "be classified by status and message, carrying the type rather than the status code" in {
      val badKey = toException(
        401,
        """{"error":{"message":"Invalid API key provided. Ensure your API key is correct and active.","type":"invalid_api_key","code":401}}"""
      )
      badKey shouldBe a[TypeSafeScalaUnauthorizedException]
      badKey.errorType shouldBe Some("invalid_api_key")

      val noInstructions = toException(
        400,
        """{"error":{"message":"Noul question must have criteria or instructions","type":"invalid_request","code":"400"}}""",
        Some("7a1504a6-a884-48e6-af6e-0c3140aeb6db")
      )
      noInstructions shouldBe a[TypeSafeScalaInvalidRequestException]
      noInstructions.errorType shouldBe Some("invalid_request")
      noInstructions.getMessage shouldBe
        "Code 400 : Noul question must have criteria or instructions [request 7a1504a6-a884-48e6-af6e-0c3140aeb6db]"

      val unknownModel = toException(
        400,
        """{"error":{"code":null,"message":"Invalid model 'pplx-decider-v1-27b-latest'. Permitted models can be found in the documentation at https://docs.perplexity.ai/docs/getting-started/models.","param":null,"type":"invalid_request_error"}}"""
      )
      unknownModel shouldBe a[TypeSafeScalaInvalidRequestException]
      unknownModel.errorType shouldBe Some("invalid_request_error")

      val tooLong = toException(
        400,
        """{"error":{"message":"Input length (262144) exceeds or equals model's maximum context length (262144)","type":"invalid_request","code":"400","model":"pplx-decider-v1-27b"}}"""
      )
      tooLong shouldBe a[TypeSafeScalaTokenCountExceededException]
      tooLong.errorType shouldBe Some("invalid_request")
    }

    "describe an empty or HTML body briefly, and take 413 as an invalid request" in {
      val wrongPath = toException(404, "")
      wrongPath shouldBe a[TypeSafeScalaNotFoundException]
      wrongPath.getMessage shouldBe "Code 404 : (empty body)"

      val tooBig = toException(
        413,
        """{"error":{"code":null,"message":"request body exceeds the maximum allowed size of 33554432 bytes","param":null,"type":"invalid_request_error"}}"""
      )
      tooBig shouldBe a[TypeSafeScalaInvalidRequestException]
      tooBig.getMessage should include("33554432 bytes")

      val gatewayPage = "<html><body>" + ("504 Gateway Time-out " * 40) + "</body></html>"
      val timedOut = toException(504, gatewayPage)
      timedOut shouldBe a[TypeSafeScalaServerErrorException]
      timedOut.getMessage.length should be < 330
      timedOut.getMessage should endWith("...")
    }

    "keep Liquid's naming code over its type" in {
      toException(
        429,
        """{"error":{"message":"The model `d1:free` is receiving too many requests.","type":"rate_limit_error","param":null,"code":"model_rate_limited"}}"""
      ).errorType shouldBe Some("model_rate_limited")
    }
  }

  "Microsoft Foundry's error bodies (Azure's OpenAI-style 400s, a Go backend's 422s)" should {
    // collected against a Foundry resource on 2026-10-09

    "be classified by status and message, carrying Azure's error code" in {
      val badShape = toException(
        400,
        """{"error":{"code":"unsupported_request_argument","message":"Model does not support request argument supplied: choice question team requires 2-255 options","details":"Model does not support request argument supplied: choice question team requires 2-255 options"}}"""
      )
      badShape shouldBe a[TypeSafeScalaInvalidRequestException]
      badShape.errorType shouldBe Some("unsupported_request_argument")
      badShape.getMessage should include("requires 2-255 options")

      val unknownField = toException(
        400,
        """{"error":{"code":"unrecognized_request_argument","message":"Unrecognized request argument supplied: images","details":"Unrecognized request argument supplied: images"}}"""
      )
      unknownField shouldBe a[TypeSafeScalaInvalidRequestException]
      unknownField.errorType shouldBe Some("unrecognized_request_argument")

      val noDeployment = toException(
        404,
        """{"error":{"code":"DeploymentNotFound","message":"The API deployment microsoft-decision-1 does not exist. If you created the deployment within the last 5 minutes, please wait a moment and try again.","details":"The API deployment microsoft-decision-1 does not exist. If you created the deployment within the last 5 minutes, please wait a moment and try again."}}"""
      )
      noDeployment shouldBe a[TypeSafeScalaNotFoundException]
      noDeployment.errorType shouldBe Some("DeploymentNotFound")
      noDeployment.getMessage should include("deployment microsoft-decision-1 does not exist")

      // Azure's gateway: the code is the status, so no error type
      val badKey = toException(
        401,
        """{"error":{"code":"401","message":"Access denied due to invalid subscription key or wrong API endpoint. Make sure to provide a valid key for an active subscription and use a correct regional API endpoint for your resource."}}"""
      )
      badKey shouldBe a[TypeSafeScalaUnauthorizedException]
      badKey.errorType shouldBe None
      badKey.getMessage should startWith("Code 401 : Access denied")
    }

    "take the token limit (a 422) as a token-count exception, the other 422s and the 413 as invalid requests" in {
      val tooLong = toException(
        422,
        """{"detail":"invalid TypeSafe request: request is about 165021 tokens; the limit for the state plus all questions is 64000"}"""
      )
      tooLong shouldBe a[TypeSafeScalaTokenCountExceededException]
      tooLong.getMessage shouldBe
        "Code 422 : invalid TypeSafe request: request is about 165021 tokens; the limit for the state plus all questions is 64000"

      val tooMany = toException(
        422,
        """{"detail":"invalid TypeSafe request: request has 256 questions; the limit is 255"}"""
      )
      tooMany shouldBe a[TypeSafeScalaInvalidRequestException]
      tooMany.getMessage should include("256 questions; the limit is 255")

      val numericLevels = toException(
        422,
        """{"detail":"invalid TypeSafe request: invalid criteria for question \"anger\": value must be a string, object, or array"}"""
      )
      numericLevels shouldBe a[TypeSafeScalaInvalidRequestException]

      val tooBig =
        toException(413, """{"detail":"request body too large: limit is 1048576 bytes"}""")
      tooBig shouldBe a[TypeSafeScalaInvalidRequestException]
      tooBig.getMessage should include("1048576 bytes")
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
