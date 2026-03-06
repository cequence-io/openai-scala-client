package io.cequence.openaiscala.gemini.service

import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaServerErrorException,
  OpenAIScalaTokenCountExceededException,
  OpenAIScalaUnauthorizedException
}

import scala.concurrent.Future

package object impl {

  /**
   * Repackages Gemini exceptions as OpenAI exceptions for consistent error handling in adapter
   * services.
   */
  def repackAsOpenAIException[T]: PartialFunction[Throwable, Future[T]] = {
    case e: GeminiScalaTokenCountExceededException =>
      Future.failed(new OpenAIScalaTokenCountExceededException(e.getMessage, e))
    case e: GeminiScalaUnauthorizedException =>
      Future.failed(new OpenAIScalaUnauthorizedException(e.getMessage, e))
    case e: GeminiScalaRateLimitException =>
      Future.failed(new OpenAIScalaRateLimitException(e.getMessage, e))
    case e: GeminiScalaServerErrorException =>
      Future.failed(new OpenAIScalaServerErrorException(e.getMessage, e))
    case e: GeminiScalaEngineOverloadedException =>
      Future.failed(new OpenAIScalaEngineOverloadedException(e.getMessage, e))
    case e: GeminiScalaClientTimeoutException =>
      Future.failed(new OpenAIScalaClientTimeoutException(e.getMessage, e))
    case e: GeminiScalaClientUnknownHostException =>
      Future.failed(new OpenAIScalaClientUnknownHostException(e.getMessage, e))
    case e: GeminiScalaNotFoundException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
    case e: GeminiScalaClientException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
  }
}
