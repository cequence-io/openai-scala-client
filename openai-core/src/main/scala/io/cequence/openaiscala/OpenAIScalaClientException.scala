package io.cequence.openaiscala

import io.cequence.wsclient.domain.CequenceWSException

object Retryable {

  def unapply(
    t: OpenAIScalaClientException
  ): Option[OpenAIScalaClientException] = Some(t).filter(apply)

  def apply(t: OpenAIScalaClientException): Boolean = t match {
    // we don't retry on these
    case _: OpenAIScalaClientUnknownHostException  => false
    case _: OpenAIScalaTokenCountExceededException => false
    case _: OpenAIScalaUnauthorizedException       => false

    // we retry on these
    case _: OpenAIScalaClientTimeoutException      => true
    case _: OpenAIScalaRateLimitException          => true
    case _: OpenAIScalaServerErrorException        => true
    case _: OpenAIScalaEngineOverloadedException   => true
    // generic case
    case _: OpenAIScalaClientException             => true
  }
}

class OpenAIScalaClientException(
  message: String,
  cause: Throwable
) extends CequenceWSException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaClientTimeoutException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaClientUnknownHostException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaTokenCountExceededException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaUnauthorizedException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaRateLimitException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaServerErrorException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaEngineOverloadedException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}
