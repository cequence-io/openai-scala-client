package io.cequence.openaiscala.service

import io.cequence.openaiscala._

/**
 * The shared HTTP status -> `OpenAIScala*` exception policy (used by `HandleOpenAIErrorCodes`
 * and, as the fallback after their own classification, by provider modules such as Vertex AI).
 *
 * Any HTTP status code >= 500 (500, 502 Bad Gateway, 504 Gateway Timeout, etc. - typically
 * emitted by a gateway/proxy such as nginx, CloudFront, or Azure sitting in front of the API,
 * rather than by the provider itself) not otherwise mapped below is treated as a transient
 * server error ([[OpenAIScalaServerErrorException]]) so that
 * [[io.cequence.openaiscala.Retryable]] flags it and
 * [[io.cequence.openaiscala.service.adapter.RetryServiceAdapter]] retries it.
 */
object OpenAIErrorCodes {

  def toException(
    httpCode: Int,
    message: String
  ): OpenAIScalaClientException = {
    val errorMessage = s"Code ${httpCode} : ${message}"
    httpCode match {
      case 401 => new OpenAIScalaUnauthorizedException(errorMessage)
      case 403 => new OpenAIScalaUnauthorizedException(errorMessage)
      case 408 => new OpenAIScalaClientTimeoutException(errorMessage)
      case 429 => new OpenAIScalaRateLimitException(errorMessage)
      case 498 => new OpenAIScalaCapacityExceededException(errorMessage)
      case 503 => new OpenAIScalaEngineOverloadedException(errorMessage)
      case 529 => new OpenAIScalaEngineOverloadedException(errorMessage)
      case 400 =>
        // a token limit is the typed OpenAIScalaTokenCountExceededException - batch splitters
        // rely on it. The embeddings endpoint has two (live 2026-10-09, text-embedding-3-small):
        // one input over 8,192 tokens ("Invalid 'input[0]': maximum input length is 8192
        // tokens.") and a request over 300k tokens in all ({"error": {"message": "Requested
        // 468160 tokens, max 300000 tokens per request", "type": "max_tokens_per_request", ...})
        if (
          message.contains("Please reduce your prompt; or completion length") ||
          message.contains("Please reduce the length of the messages") ||
          message.contains("maximum input length is") ||
          message.contains("maximum context length is") ||
          message.contains("max_tokens_per_request") ||
          // the Decisions API: "Decision input exceeds the token limit." (live 2026-10-07)
          message.contains("exceeds the token limit")
        )
          new OpenAIScalaTokenCountExceededException(errorMessage)
        else
          new OpenAIScalaClientException(errorMessage)

      case code if code >= 500 => new OpenAIScalaServerErrorException(errorMessage)

      case _ => new OpenAIScalaClientException(errorMessage)
    }
  }
}
