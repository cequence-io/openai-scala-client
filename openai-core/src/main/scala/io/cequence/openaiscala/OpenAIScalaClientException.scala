package io.cequence.openaiscala

object Retryable {

  def unapply(
    t: OpenAIScalaClientException
  ): Option[OpenAIScalaClientException] = Some(t).filter(apply)

  def apply(t: OpenAIScalaClientException): Boolean = t match {
    // TODO: Need separate classes for rate-limit-error conditions (#16)
    case _: OpenAIScalaClientTimeoutException => true
    case _                                    => false
  }

}

class OpenAIScalaClientException(
  message: String,
  cause: Throwable
) extends RuntimeException(message, cause) {
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
