package io.cequence.openaiscala.service

import io.cequence.openaiscala._
import io.cequence.wsclient.service.ws.WSRequestHelper

/**
 * Core WS stuff for OpenAI services.
 *
 * @since March
 *   2024
 */
trait OpenAIWSRequestHelper extends WSRequestHelper {

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing = {
    val errorMessage = s"Code ${httpCode} : ${message}"
    httpCode match {
      case 401 => throw new OpenAIScalaUnauthorizedException(errorMessage)
      case 429 => throw new OpenAIScalaRateLimitException(errorMessage)
      case 500 => throw new OpenAIScalaServerErrorException(errorMessage)
      case 503 => throw new OpenAIScalaEngineOverloadedException(errorMessage)
      case 400 =>
        if (
          message.contains("Please reduce your prompt; or completion length") ||
          message.contains("Please reduce the length of the messages")
        )
          throw new OpenAIScalaTokenCountExceededException(errorMessage)
        else
          throw new OpenAIScalaClientException(errorMessage)

      case _ => throw new OpenAIScalaClientException(errorMessage)
    }
  }
}
