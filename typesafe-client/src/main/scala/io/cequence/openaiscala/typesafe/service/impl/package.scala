package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala._

import scala.concurrent.Future

package object impl {

  /**
   * The OpenAI adapter's view of the native exceptions: every [[TypeSafeScalaClientException]]
   * becomes the matching `OpenAIScala*` one (so the shared `Retryable` matcher, routers and
   * retry adapters treat it right), with the native exception - and its `httpCode` /
   * `errorType` / `requestId` - as the cause.
   */
  def repackAsOpenAIException[T]: PartialFunction[Throwable, Future[T]] = {
    case e: TypeSafeScalaTokenCountExceededException =>
      Future.failed(new OpenAIScalaTokenCountExceededException(e.getMessage, e))
    case e: TypeSafeScalaUnauthorizedException =>
      Future.failed(new OpenAIScalaUnauthorizedException(e.getMessage, e))
    case e: TypeSafeScalaRateLimitException =>
      Future.failed(new OpenAIScalaRateLimitException(e.getMessage, e))
    case e: TypeSafeScalaEngineOverloadedException =>
      Future.failed(new OpenAIScalaEngineOverloadedException(e.getMessage, e))
    case e: TypeSafeScalaServerErrorException =>
      Future.failed(new OpenAIScalaServerErrorException(e.getMessage, e))
    case e: TypeSafeScalaClientTimeoutException =>
      Future.failed(new OpenAIScalaClientTimeoutException(e.getMessage, e))
    case e: TypeSafeScalaClientUnknownHostException =>
      Future.failed(new OpenAIScalaClientUnknownHostException(e.getMessage, e))
    // api usage, invalid request, not found and the base: plain client errors
    case e: TypeSafeScalaClientException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
  }
}
