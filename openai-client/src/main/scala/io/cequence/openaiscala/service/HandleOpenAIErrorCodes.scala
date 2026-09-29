package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.service.WSClient

/**
 * Core WS stuff for OpenAI services: HTTP errors are classified by the shared
 * [[OpenAIErrorCodes]] policy (e.g. any unmapped status >= 500 is a transient, retryable
 * server error).
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

  /** The shared policy of [[OpenAIErrorCodes]] (kept here for source compatibility). */
  def toException(
    httpCode: Int,
    message: String
  ): OpenAIScalaClientException =
    OpenAIErrorCodes.toException(httpCode, message)
}
