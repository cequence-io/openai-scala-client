package io.cequence.openaiscala.anthropic.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HandleAnthropicErrorCodesMappingSpec extends AnyWordSpec with Matchers {

  "HandleAnthropicErrorCodes.toException" should {

    val cases: Seq[(Int, String, Class[_ <: AnthropicScalaClientException])] = Seq(
      (401, "unauthorized", classOf[AnthropicScalaUnauthorizedException]),
      (403, "forbidden", classOf[AnthropicScalaUnauthorizedException]),
      (404, "not found", classOf[AnthropicScalaNotFoundException]),
      (408, "request timeout", classOf[AnthropicScalaClientTimeoutException]),
      (429, "too many requests", classOf[AnthropicScalaRateLimitException]),
      (500, "internal server error", classOf[AnthropicScalaServerErrorException]),
      (502, "bad gateway", classOf[AnthropicScalaServerErrorException]),
      (503, "service unavailable", classOf[AnthropicScalaEngineOverloadedException]),
      (504, "gateway timeout", classOf[AnthropicScalaServerErrorException]),
      (529, "overloaded", classOf[AnthropicScalaEngineOverloadedException]),
      (418, "i'm a teapot", classOf[AnthropicScalaClientException]),
      (400, "prompt is too long", classOf[AnthropicScalaTokenCountExceededException]),
      (
        400,
        "input is too long for requested model",
        classOf[AnthropicScalaTokenCountExceededException]
      ),
      (400, "some other bad request message", classOf[AnthropicScalaClientException])
    )

    cases.foreach { case (code, message, expectedClass) =>
      s"map code $code with message '$message' to ${expectedClass.getSimpleName}" in {
        val exception = HandleAnthropicErrorCodes.toException(code, message)
        exception.getClass shouldBe expectedClass
        exception.getMessage shouldBe s"Code ${code} : ${message}"
      }
    }

    "map all the token-count-exceeded substring rules for 400 (case-insensitive)" in {
      val messages = Seq(
        "input length and `max_tokens` exceed context limit",
        "Prompt Is Too Long",
        "which is the maximum allowed number of output tokens",
        "Input is too long for requested model"
      )

      messages.foreach { message =>
        HandleAnthropicErrorCodes
          .toException(400, message)
          .getClass shouldBe classOf[AnthropicScalaTokenCountExceededException]
      }
    }
  }
}
