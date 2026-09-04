package io.cequence.openaiscala.service

import io.cequence.openaiscala._
import io.cequence.wsclient.service.WSClient

/**
 * Core WS stuff for OpenAI services.
 *
 * Any HTTP status code >= 500 (500, 502 Bad Gateway, 504 Gateway Timeout, etc. - typically
 * emitted by a gateway/proxy such as nginx, CloudFront, or Azure sitting in front of the API,
 * rather than by OpenAI itself) not otherwise mapped below is treated as a transient server
 * error ([[OpenAIScalaServerErrorException]]) so that [[io.cequence.openaiscala.Retryable]]
 * flags it and [[io.cequence.openaiscala.service.adapter.RetryServiceAdapter]] retries it.
 *
 * @since March
 *   2024
 */
trait HandleOpenAIErrorCodes extends WSClient {

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing =
    throw HandleOpenAIErrorCodes.toException(httpCode, message)
}

object HandleOpenAIErrorCodes {

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
        if (
          message.contains("Please reduce your prompt; or completion length") ||
          message.contains("Please reduce the length of the messages") ||
          message.contains("maximum input length is") ||
          message.contains("maximum context length is")
        )
          new OpenAIScalaTokenCountExceededException(errorMessage)
        else
          new OpenAIScalaClientException(errorMessage)

      case code if code >= 500 => new OpenAIScalaServerErrorException(errorMessage)

      case _ => new OpenAIScalaClientException(errorMessage)
    }
  }
}
