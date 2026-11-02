package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaJsonParseException
import io.cequence.openaiscala.OpenAIScalaModelSkippedException
import io.cequence.openaiscala.OpenAIScalaTokenCountExceededException
import io.cequence.openaiscala.domain.response.ChatCompletionResponse

import scala.concurrent.Future
import scala.concurrent.duration.{Deadline, Duration, FiniteDuration}

/**
 * Tunes the model failover chain of `createChatCompletionWithFailover*` and
 * `createChatCompletionWithJSON*`. The default policy changes nothing: no time limits, no stop
 * condition, no parse re-asks, no listener, the last model's error ends a failed chain.
 *
 * Checks before every new try, in this order: the deadline, then the stop flag; a try is
 * limited by the smaller of `attemptTimeout` and the time left to the deadline.
 *
 * @param attemptTimeout
 *   Limit of ONE HTTP try (not of a model's whole ladder of retries). A try over the limit is
 *   abandoned (its call keeps running, see [[AttemptOutcome.LateAnswer]]) and the chain fails
 *   over to the next model at once - a timed-out try is never retried on the same model.
 * @param deadline
 *   No new try is started once overdue (the chain fails with
 *   [[io.cequence.openaiscala.OpenAIScalaChainStoppedException]]); the time left also caps a
 *   try's limit.
 * @param shouldStop
 *   Checked before every new try (also after a backoff sleep); true stops the chain with
 *   [[io.cequence.openaiscala.OpenAIScalaChainStoppedException]].
 * @param parseRetries
 *   JSON helpers only: how many times the SAME model is asked again after an unparseable
 *   answer (no backoff).
 * @param failoverOnParseError
 *   JSON helpers only: after the re-asks, move on to the next model instead of failing the
 *   call with the parse error.
 * @param onAttempt
 *   Sees the outcome of every try, late ones included. Must be thread-safe.
 * @param wrapAttempt
 *   Wraps the raw call of every try (not the timeout race).
 * @param finalError
 *   Picks the error of a chain that ran out of models, given each model's final error in chain
 *   order.
 * @param failoverLogLevel
 *   The level the failover from a model (and the final "no more models" line) is logged at,
 *   chosen by the error that ended the model. At `Error` (the default for most errors) the
 *   stack trace is logged, at the other levels the message and the error's message only. E.g.
 *   a guard that rules a model out is a warning, not an error
 *   ([[io.cequence.openaiscala.OpenAIScalaModelSkippedException]], `Warn` by default). A
 *   partial-function literal with a fallback to the default works: `{ case _: MyException =>
 *   FailoverLogLevel.Info; case e => FailoverLogLevel.default(e) }`.
 */
final case class FailoverPolicy(
  attemptTimeout: Option[FiniteDuration] = None,
  deadline: Option[Deadline] = None,
  shouldStop: () => Boolean = FailoverPolicy.neverStop,
  parseRetries: Int = 0,
  failoverOnParseError: Boolean = false,
  onAttempt: AttemptListener = AttemptListener.none,
  wrapAttempt: AttemptWrapper = AttemptWrapper.identity,
  finalError: Seq[Throwable] => Throwable = FailoverPolicy.lastError,
  failoverLogLevel: Throwable => FailoverLogLevel = FailoverLogLevel.default
) {
  require(parseRetries >= 0, "parseRetries must not be negative.")
  require(
    attemptTimeout.forall(_ > Duration.Zero),
    "attemptTimeout must be positive."
  )
}

object FailoverPolicy {

  /** A stop flag that never stops. */
  val neverStop: () => Boolean = () => false

  /** The last model's error (the default). */
  val lastError: Seq[Throwable] => Throwable = _.last

  /** The first `OpenAIScalaTokenCountExceededException` of any model, else the last error. */
  val tokenCountExceededFirst: Seq[Throwable] => Throwable = errors =>
    errors.collectFirst { case e: OpenAIScalaTokenCountExceededException => e }
      .getOrElse(errors.last)

  // declared last: the defaults above must be initialized before this instance is built
  /** Changes nothing against a chain without a policy. */
  val default: FailoverPolicy = FailoverPolicy()
}

/** The log level of a failover line, see [[FailoverPolicy.failoverLogLevel]]. */
sealed trait FailoverLogLevel

object FailoverLogLevel {
  case object Error extends FailoverLogLevel
  case object Warn extends FailoverLogLevel
  case object Info extends FailoverLogLevel
  case object Debug extends FailoverLogLevel

  /** `Warn` for a skipped model, `Error` for everything else. */
  val default: Throwable => FailoverLogLevel = {
    case _: OpenAIScalaModelSkippedException => Warn
    case _                                   => Error
  }
}

/** What kind of try an [[AttemptInfo]] describes. */
sealed trait AttemptKind

object AttemptKind {

  /** The first try of a model. */
  case object First extends AttemptKind

  /** A retry of the same model after a retryable error (after the backoff). */
  case object TransientRetry extends AttemptKind

  /** A re-ask of the same model after an unparseable answer. */
  case object ParseReask extends AttemptKind
}

/**
 * Identifies a try.
 *
 * @param requestedModel
 *   The settings' model id (price by it; `response.model` is the provider's echo, often a
 *   dated build).
 * @param modelIndex
 *   0-based position of the model in the chain (after `filterModels`).
 * @param tryNumber
 *   1-based per model, counting every kind of try.
 * @param kind
 *   Why this try was made.
 */
final case class AttemptInfo(
  requestedModel: String,
  modelIndex: Int,
  tryNumber: Int,
  kind: AttemptKind
)

/** The outcome of a try, reported to an [[AttemptListener]]. */
sealed trait AttemptOutcome

object AttemptOutcome {

  /** Accepted (for the JSON helpers: parsed). */
  final case class Answered(response: ChatCompletionResponse) extends AttemptOutcome

  /** Answered, but the JSON helper could not parse it. The response was billed. */
  final case class Unparseable(
    response: ChatCompletionResponse,
    error: OpenAIScalaJsonParseException
  ) extends AttemptOutcome

  /** No response (including a synchronous throw). */
  final case class Failed(error: Throwable) extends AttemptOutcome

  /** The try exceeded its limit; the chain moved on, the call still runs. */
  final case class TimedOut(limit: FiniteDuration) extends AttemptOutcome

  /** A timed-out try answered later (billed!). */
  final case class LateAnswer(response: ChatCompletionResponse) extends AttemptOutcome

  /** A timed-out try failed later. */
  final case class LateFailure(error: Throwable) extends AttemptOutcome
}

/**
 * Receives the outcome of every try. May be called concurrently (late outcomes arrive from
 * other threads), so it must be thread-safe. A throwing listener is logged and ignored.
 */
trait AttemptListener {
  def apply(
    info: AttemptInfo,
    outcome: AttemptOutcome
  ): Unit
}

object AttemptListener {
  val none: AttemptListener = new AttemptListener {
    override def apply(
      info: AttemptInfo,
      outcome: AttemptOutcome
    ): Unit = ()
  }
}

/**
 * Wraps the RAW call of every try (not the timeout race), so e.g. an in-flight counter still
 * sees a timed-out call until it really lands.
 */
trait AttemptWrapper {
  def apply(
    info: AttemptInfo,
    call: () => Future[ChatCompletionResponse]
  ): Future[ChatCompletionResponse]
}

object AttemptWrapper {
  val identity: AttemptWrapper = new AttemptWrapper {
    override def apply(
      info: AttemptInfo,
      call: () => Future[ChatCompletionResponse]
    ): Future[ChatCompletionResponse] = call()
  }
}
