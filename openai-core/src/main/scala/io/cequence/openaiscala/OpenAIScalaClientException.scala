package io.cequence.openaiscala

import io.cequence.openaiscala.domain.guardrails.{GuardrailStage, GuardrailVerdict}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.wsclient.domain.CequenceWSException

import scala.concurrent.duration.FiniteDuration

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
 * A single try of a model failover chain exceeded its time limit
 * (`FailoverPolicy.attemptTimeout` or the time left to the policy's deadline). The call itself
 * may still be running and billed; the chain moves on to the next model without retrying this
 * one. Deliberately NOT [[Retryable]] and NOT a subtype of
 * [[OpenAIScalaClientTimeoutException]]: a chain that ends with it already gave every model
 * its try, and a retry around the whole call would run the chain again while the abandoned
 * calls are still running (and billed).
 */
class OpenAIScalaAttemptTimeoutException(
  message: String,
  val model: String,
  val limit: FiniteDuration
) extends OpenAIScalaClientException(message)

/**
 * A model must not be used for this request. Thrown by a guard (from
 * `FailoverPolicy.wrapAttempt`, synchronously or as a failed Future, or by a decorating
 * service). The failover chain fails over at once, NEVER retries it on the same model (even
 * with `retryOnAnyError` - like a timeout) and logs the failover at WARN by default (no stack
 * trace, see `FailoverPolicy.failoverLogLevel`). Generic, so not [[Retryable]].
 */
class OpenAIScalaModelSkippedException(
  message: String,
  val model: String,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause) {
  def this(
    message: String,
    model: String
  ) = this(message, model, null)
}

/**
 * A model failover chain was stopped by its policy (the stop flag or the deadline) before it
 * could make another try. Deliberately NOT [[Retryable]].
 *
 * @param attempts
 *   tries made so far
 * @param byDeadline
 *   true if the deadline was the reason, false for the stop flag
 * @param cause
 *   the most recent error of the chain, or null if there was none
 */
class OpenAIScalaChainStoppedException(
  message: String,
  val attempts: Int,
  val byDeadline: Boolean,
  cause: Throwable
) extends OpenAIScalaClientException(message, cause)

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

/**
 * A guardrail blocked a chat call - `OpenAIServiceAdapters.guardrails` with the default
 * `GuardrailAction.Reject`. Carries the blocking verdicts (one per guardrail that blocked);
 * when a guard could not answer and its guardrail fails closed, the guard's error is the
 * cause. Deliberately NOT [[Retryable]]: the call was judged, not lost - asking again (with
 * the same input) is the caller's decision.
 */
class OpenAIScalaGuardrailException(
  val verdicts: Seq[GuardrailVerdict]
) extends OpenAIScalaClientException(
      GuardrailVerdict.describe(verdicts),
      verdicts.flatMap(_.failure).headOption.orNull
    ) {
  require(verdicts.nonEmpty, "A guardrail exception needs at least one blocking verdict.")

  /** The first blocking verdict. */
  def verdict: GuardrailVerdict = verdicts.head

  /** Whether the input or a reply was blocked. */
  def stage: GuardrailStage = verdict.stage
}
