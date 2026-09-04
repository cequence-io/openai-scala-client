package io.cequence.openaiscala.anthropic.service

import io.cequence.wsclient.service.WSClient

/**
 * Handle error codes for the Anthropic service.
 *
 * Any HTTP status code >= 500 (500, 502 Bad Gateway, 504 Gateway Timeout, etc. - typically
 * emitted by a gateway/proxy such as nginx, CloudFront, or Azure sitting in front of the API,
 * rather than by Anthropic itself) not otherwise mapped below is treated as a transient server
 * error ([[AnthropicScalaServerErrorException]]) so that the retry adapter retries it.
 *
 * @since March
 *   2024
 */
trait HandleAnthropicErrorCodes extends WSClient {

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing =
    throw HandleAnthropicErrorCodes.toException(httpCode, message)
}

object HandleAnthropicErrorCodes {

  private val TokenCountExceededMessages = Set(
    "input length and `max_tokens` exceed context limit",
    "prompt is too long",
    "which is the maximum allowed number of output tokens",
    "input is too long for requested model" // bedrock
  )

  def toException(
    httpCode: Int,
    message: String
  ): AnthropicScalaClientException = {
    val errorMessage = s"Code ${httpCode} : ${message}"
    httpCode match {

      case 400 => {
        // Check if the error message indicates token count exceeded
        if (TokenCountExceededMessages.exists(message.toLowerCase().contains)) {
          new AnthropicScalaTokenCountExceededException(errorMessage)
        } else {
          // 400 - invalid_request_error: There was an issue with the format or content of your request.
          // We may also use this error type for other 4XX status codes not listed below.
          new AnthropicScalaClientException(errorMessage)
        }
      }

      // 401 - authentication_error: There’s an issue with your API key.
      case 401 => new AnthropicScalaUnauthorizedException(errorMessage)

      // 403 - permission_error: Your API key does not have permission to use the specified
      // resource.
      case 403 => new AnthropicScalaUnauthorizedException(errorMessage)

      // 404 - not_found_error: The requested resource was not found.
      case 404 => new AnthropicScalaNotFoundException(errorMessage)

      // 408 - request_timeout: The request timed out (e.g. a gateway/proxy in front of the
      // API).
      case 408 => new AnthropicScalaClientTimeoutException(errorMessage)

      // 429 - rate_limit_error: Your account has hit a rate limit.
      case 429 => new AnthropicScalaRateLimitException(errorMessage)

      // 503 / 529 - overloaded_error: Anthropic’s API (or a gateway in front of it) is
      // temporarily overloaded/unavailable.
      case 503 => new AnthropicScalaEngineOverloadedException(errorMessage)
      case 529 => new AnthropicScalaEngineOverloadedException(errorMessage)

      // any other >= 500 - api_error: an unexpected/transient server-side error, incl.
      // gateway errors (502 Bad Gateway, 504 Gateway Timeout) from a proxy in front of
      // Anthropic's API.
      case code if code >= 500 => new AnthropicScalaServerErrorException(errorMessage)

      case _ => new AnthropicScalaClientException(errorMessage)
    }
  }
}
