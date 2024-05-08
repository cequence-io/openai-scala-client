package io.cequence.openaiscala

object Retryable {

  def unapply(
    t: OpenAIScalaClientException
  ): Option[OpenAIScalaClientException] = Some(t).filter(apply)

  // TODO: consider retrying for Anthropic
  def apply(t: OpenAIScalaClientException): Boolean = t match {
    // we retry on these
    case _: OpenAIScalaClientTimeoutException    => true
    case _: OpenAIScalaRateLimitException        => true
    case _: OpenAIScalaServerErrorException      => true
    case _: OpenAIScalaEngineOverloadedException => true

    // otherwise don't retry
    case _ => false
  }
}

class CequenceWSException(
  message: String,
  cause: Throwable
) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
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
