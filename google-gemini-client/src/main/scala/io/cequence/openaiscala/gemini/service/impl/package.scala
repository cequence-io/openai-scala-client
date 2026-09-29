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
   * Pure mapping of a Gemini exception to its OpenAI-adapter equivalent (the Gemini one as the
   * cause). Any other throwable passes through unchanged, so this is total - safe to use
   * directly with `Source.mapError`.
   */
  def toOpenAIException: PartialFunction[Throwable, Throwable] = {
    case e: GeminiScalaTokenCountExceededException =>
      new OpenAIScalaTokenCountExceededException(e.getMessage, e)
    case e: GeminiScalaUnauthorizedException =>
      new OpenAIScalaUnauthorizedException(e.getMessage, e)
    case e: GeminiScalaRateLimitException =>
      new OpenAIScalaRateLimitException(e.getMessage, e)
    case e: GeminiScalaServerErrorException =>
      new OpenAIScalaServerErrorException(e.getMessage, e)
    case e: GeminiScalaEngineOverloadedException =>
      new OpenAIScalaEngineOverloadedException(e.getMessage, e)
    case e: GeminiScalaClientTimeoutException =>
      new OpenAIScalaClientTimeoutException(e.getMessage, e)
    case e: GeminiScalaClientUnknownHostException =>
      new OpenAIScalaClientUnknownHostException(e.getMessage, e)
    case e: GeminiScalaNotFoundException =>
      new OpenAIScalaClientException(e.getMessage, e)
    case e: GeminiScalaClientException =>
      new OpenAIScalaClientException(e.getMessage, e)
    case e =>
      e
  }

  /**
   * Repackages Gemini exceptions as OpenAI exceptions for consistent error handling in adapter
   * services.
   */
  def repackAsOpenAIException[T]: PartialFunction[Throwable, Future[T]] = {
    case e: GeminiScalaClientException => Future.failed(toOpenAIException(e))
  }
}
