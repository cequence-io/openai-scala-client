package io.cequence.openaiscala.gemini.service

import io.cequence.wsclient.service.WSClient

/**
 * Handle error codes for the Google Gemini service.
 *
 * @since March
 *   2026
 */
trait HandleGeminiErrorCodes extends WSClient {

  private val TokenCountExceededMessages = Set(
    "request payload size exceeds the limit",
    "input token count exceeds the maximum number of tokens allowed"
  )

  private val UnauthorizedMessages = Set(
    "api key not valid"
  )

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing = {
    val errorMessage = s"Code ${httpCode} : ${message}"
    val messageLower = message.toLowerCase()
    httpCode match {

      case 400 => {
        if (TokenCountExceededMessages.exists(messageLower.contains)) {
          // 400 - token/payload limit exceeded
          throw new GeminiScalaTokenCountExceededException(errorMessage)
        } else if (UnauthorizedMessages.exists(messageLower.contains)) {
          // 400 - INVALID_ARGUMENT: API key not valid (Gemini returns 400 for invalid API keys)
          throw new GeminiScalaUnauthorizedException(errorMessage)
        } else {
          // 400 - INVALID_ARGUMENT / FAILED_PRECONDITION
          throw new GeminiScalaClientException(errorMessage)
        }
      }

      // 401 - UNAUTHENTICATED: API key not valid.
      case 401 => throw new GeminiScalaUnauthorizedException(errorMessage)

      // 403 - PERMISSION_DENIED: API key does not have the required permissions.
      case 403 => throw new GeminiScalaUnauthorizedException(errorMessage)

      // 404 - NOT_FOUND: The requested resource was not found.
      case 404 => throw new GeminiScalaNotFoundException(errorMessage)

      // 429 - RESOURCE_EXHAUSTED: Rate limit exceeded.
      case 429 => throw new GeminiScalaRateLimitException(errorMessage)

      // 500 - INTERNAL: An unexpected error on Google's side.
      case 500 => throw new GeminiScalaServerErrorException(errorMessage)

      // 503 - UNAVAILABLE: The service is temporarily overloaded or unavailable.
      case 503 => throw new GeminiScalaEngineOverloadedException(errorMessage)

      // 504 - DEADLINE_EXCEEDED: Request took too long to process.
      case 504 => throw new GeminiScalaClientTimeoutException(errorMessage)

      case _ => throw new GeminiScalaClientException(errorMessage)
    }
  }
}
