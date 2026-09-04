package io.cequence.openaiscala.service

import io.cequence.openaiscala._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HandleOpenAIErrorCodesMappingSpec extends AnyWordSpec with Matchers {

  "HandleOpenAIErrorCodes.toException" should {

    val cases: Seq[(Int, String, Class[_ <: OpenAIScalaClientException])] = Seq(
      (401, "unauthorized", classOf[OpenAIScalaUnauthorizedException]),
      (403, "forbidden", classOf[OpenAIScalaUnauthorizedException]),
      (408, "request timeout", classOf[OpenAIScalaClientTimeoutException]),
      (429, "too many requests", classOf[OpenAIScalaRateLimitException]),
      (498, "capacity exceeded", classOf[OpenAIScalaCapacityExceededException]),
      (500, "internal server error", classOf[OpenAIScalaServerErrorException]),
      (502, "bad gateway", classOf[OpenAIScalaServerErrorException]),
      (503, "service unavailable", classOf[OpenAIScalaEngineOverloadedException]),
      (504, "gateway timeout", classOf[OpenAIScalaServerErrorException]),
      (529, "overloaded", classOf[OpenAIScalaEngineOverloadedException]),
      (418, "i'm a teapot", classOf[OpenAIScalaClientException]),
      (
        400,
        "Please reduce your prompt; or completion length",
        classOf[OpenAIScalaTokenCountExceededException]
      ),
      (400, "some other bad request message", classOf[OpenAIScalaClientException])
    )

    cases.foreach { case (code, message, expectedClass) =>
      s"map code $code to ${expectedClass.getSimpleName}" in {
        val exception = HandleOpenAIErrorCodes.toException(code, message)
        exception.getClass shouldBe expectedClass
        exception.getMessage shouldBe s"Code ${code} : ${message}"
      }
    }

    "map all the token-count-exceeded substring rules for 400" in {
      val messages = Seq(
        "Please reduce your prompt; or completion length",
        "Please reduce the length of the messages",
        "maximum input length is 4096",
        "maximum context length is 8192"
      )

      messages.foreach { message =>
        HandleOpenAIErrorCodes
          .toException(400, message)
          .getClass shouldBe classOf[OpenAIScalaTokenCountExceededException]
      }
    }

    Seq(502, 504, 408, 429, 503, 529).foreach { code =>
      s"be retryable for code $code" in {
        Retryable(HandleOpenAIErrorCodes.toException(code, "x")) shouldBe true
      }
    }

    Seq(401, 400, 404).foreach { code =>
      s"NOT be retryable for code $code" in {
        Retryable(HandleOpenAIErrorCodes.toException(code, "x")) shouldBe false
      }
    }
  }
}
