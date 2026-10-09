package io.cequence.openaiscala.service

import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaTokenCountExceededException
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * `OpenAIErrorCodes.toException` on the 400 bodies the APIs answer with (every ws-client
 * engine hands the whole error body over as the message): a token limit must be the typed
 * `OpenAIScalaTokenCountExceededException` - batch splitters rely on it - while any other 400
 * stays a plain client exception, the body kept in the message.
 */
class OpenAIErrorCodesSpec extends AnyWordSpec with Matchers {

  // the embeddings endpoint's two limits (live 2026-10-09, text-embedding-3-small): one input
  // over 8,192 tokens - worded by the input's shape: an array names the element, a lone string
  // the context - and a request over 300k tokens in all
  private val embeddingsInputTooLong =
    """{"error": {"message": "Invalid 'input[0]': maximum input length is 8192 tokens.", "type": "invalid_request_error", "param": "input[0]", "code": "invalid_value"}}"""
  private val embeddingsLoneInputTooLong =
    """{
  "error": {
    "message": "Invalid 'input': maximum context length is 8192 tokens.",
    "type": "invalid_request_error",
    "param": null,
    "code": null
  }
}"""
  private val embeddingsRequestTooLong =
    """{"error": {"message": "Requested 468160 tokens, max 300000 tokens per request", "type": "max_tokens_per_request", "param": null, "code": "max_tokens_per_request"}}"""

  // chat completions over the model's context
  private val chatContextTooLong =
    """{"error": {"message": "This model's maximum context length is 128000 tokens. However, your messages resulted in 130421 tokens. Please reduce the length of the messages.", "type": "invalid_request_error", "param": "messages", "code": "context_length_exceeded"}}"""

  // the Decisions API (live 2026-10-07, 1.2M input tokens)
  private val decisionInputTooLong =
    """{"error": {"message": "Decision input exceeds the token limit.", "type": "invalid_request_error", "param": "input", "code": null}}"""

  private val otherBadRequest =
    """{"error": {"message": "Question names must be unique within the request.", "type": "invalid_request_error", "param": "questions[1].name", "code": null}}"""

  "OpenAIErrorCodes.toException" should {

    "type every token limit as OpenAIScalaTokenCountExceededException" in {
      Seq(
        embeddingsInputTooLong,
        embeddingsLoneInputTooLong,
        embeddingsRequestTooLong,
        chatContextTooLong,
        decisionInputTooLong
      ).foreach { body =>
        withClue(body) {
          OpenAIErrorCodes.toException(400, body) shouldBe
            an[OpenAIScalaTokenCountExceededException]
        }
      }
    }

    "leave any other 400 a plain client exception" in {
      val exception = OpenAIErrorCodes.toException(400, otherBadRequest)
      exception.getClass shouldBe classOf[OpenAIScalaClientException]
    }

    "keep the status and the whole body in the message" in {
      OpenAIErrorCodes.toException(400, embeddingsRequestTooLong).getMessage shouldBe
        s"Code 400 : $embeddingsRequestTooLong"
      OpenAIErrorCodes.toException(400, otherBadRequest).getMessage shouldBe
        s"Code 400 : $otherBadRequest"
    }
  }
}
