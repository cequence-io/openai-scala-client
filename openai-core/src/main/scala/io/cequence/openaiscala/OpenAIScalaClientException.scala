package io.cequence.openaiscala

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
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
    case _: OpenAIScalaClientTimeoutException    => true
    case _: OpenAIScalaRateLimitException        => true
    case _: OpenAIScalaServerErrorException      => true
    case _: OpenAIScalaEngineOverloadedException => true
    case _: OpenAIScalaCapacityExceededException => true

    // generic case
    case _: OpenAIScalaClientException => false
  }
}

class OpenAIScalaClientException(
  message: String,
  cause: Throwable
) extends CequenceWSException(message, cause) {
  def this(message: String) = this(message, null)
}

/**
 * A chat-completion batch did not finish within the caller-requested wait deadline. The batch
 * keeps processing server-side (its id is in the message), so this is deliberately NOT
 * [[Retryable]] and NOT a subtype of [[OpenAIScalaClientTimeoutException]] - resubmitting the
 * work elsewhere would double it.
 */
class OpenAIScalaBatchTimeoutException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaClientTimeoutException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

/**
 * A chat-completion call succeeded at the HTTP level but its content could not be parsed as /
 * converted to the expected JSON (even after repair). Carries the full
 * [[io.cequence.openaiscala.domain.response.ChatCompletionResponse]] so callers can still
 * account for the token usage of the failed attempt - the tokens were generated and billed by
 * the provider even though the output is unusable. Deliberately NOT [[Retryable]] at the
 * transport level: the request itself was delivered and answered, so whether to re-ask the
 * model is the caller's decision.
 */
class OpenAIScalaJsonParseException(
  message: String,
  val response: ChatCompletionResponse,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(
    message: String,
    response: ChatCompletionResponse
  ) = this(message, response, null)
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

class OpenAIScalaCapacityExceededException(
  message: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}
