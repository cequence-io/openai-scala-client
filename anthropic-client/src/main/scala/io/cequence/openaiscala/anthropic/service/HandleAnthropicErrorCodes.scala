package io.cequence.openaiscala.anthropic.service

import io.cequence.wsclient.service.WSClient

/**
 * Handle error codes for the Anthropic service.
 *
 * @since March
 *   2024
 */
trait HandleAnthropicErrorCodes extends WSClient {

  private val TokenCountExceededMessages = Set(
    "input length and `max_tokens` exceed context limit",
    "prompt is too long"
  )

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing = {
    val errorMessage = s"Code ${httpCode} : ${message}"
    httpCode match {

      case 400 => {
        // Check if the error message indicates token count exceeded
        if (TokenCountExceededMessages.exists(message.contains)) {
          throw new AnthropicScalaTokenCountExceededException(errorMessage)
        } else {
          // 400 - invalid_request_error: There was an issue with the format or content of your request.
          // We may also use this error type for other 4XX status codes not listed below.
          throw new AnthropicScalaClientException(errorMessage)
        }
      }

      // 401 - authentication_error: There’s an issue with your API key.
      case 401 => throw new AnthropicScalaUnauthorizedException(errorMessage)

      // 403 - permission_error: Your API key does not have permission to use the specified resource.
      case 403 => throw new AnthropicScalaUnauthorizedException(errorMessage)

      // 404 - not_found_error: The requested resource was not found.
      case 404 => throw new AnthropicScalaNotFoundException(errorMessage)

      // 429 - rate_limit_error: Your account has hit a rate limit.
      case 429 => throw new AnthropicScalaRateLimitException(errorMessage)

      // 500 - api_error: An unexpected error has occurred internal to Anthropic’s systems.
      case 500 => throw new AnthropicScalaServerErrorException(errorMessage)

      // 529 - overloaded_error: Anthropic’s API is temporarily overloaded.
      case 529 => throw new AnthropicScalaEngineOverloadedException(errorMessage)

      case _ => throw new AnthropicScalaClientException(errorMessage)
    }
  }
}
