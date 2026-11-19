package io.cequence.openaiscala

import io.cequence.wsclient.concurrent.AsyncScheduler
import io.cequence.wsclient.stream.AsyncCall
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.service.{AttemptInfo, AttemptKind, FailoverLogLevel}
import org.slf4j.Logger

import scala.concurrent.duration.{Deadline, Duration, FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

/**
 * THE retry / failover engine. Every retry helper (`RetryHelpers.retry`,
 * `retryOnFailureOrFailover`, the chat failover chain) delegates here. Non-blocking: the
 * engine is one lazy [[AsyncCall]] ([[runCall]]; since 2.1.0) - ZIO runs it natively, with
 * every try's request interruptible - and [[run]] is that call over Future tries, run as a
 * Future.
 *
 * Per step (e.g. a model): a ladder of tries - the first one, then retries of retryable errors
 * (with backoff) and re-asks of rejected answers; when the ladder ends the engine fails over
 * to the next step. The input to try is `steps(i)._1`, `steps(i)._2` is its label for logging
 * and [[AttemptInfo]].
 */
private[openaiscala] object RetryEngine {

  /** What happened to one try (`R` the raw result, `E` the error of a rejected one). */
  sealed trait Outcome[+R, +E]

  object Outcome {

    /** Accepted by `accept`. */
    final case class Accepted[+R](response: R) extends Outcome[R, Nothing]

    /** Answered, but `accept` refused the answer. */
    final case class Rejected[+R, +E](
      response: R,
      error: E
    ) extends Outcome[R, E]

    /** No answer (including a synchronous throw). */
    final case class Failed(error: Throwable) extends Outcome[Nothing, Nothing]

    /** The try exceeded its limit; the engine moved on, the call still runs. */
    final case class TimedOut(limit: FiniteDuration) extends Outcome[Nothing, Nothing]

    /** A timed-out try answered later. */
    final case class Late[+R](response: R) extends Outcome[R, Nothing]

    /** A timed-out try failed later. */
    final case class LateFailed(error: Throwable) extends Outcome[Nothing, Nothing]
  }

  /** (level, message, error to attach as a stack trace) */
  type FailoverLog = (FailoverLogLevel, String, Option[Throwable]) => Unit

  /** Writes to an slf4j logger at the level; an attached error comes with its stack trace. */
  def slf4jFailoverLog(logger: Logger): FailoverLog = {
    case (FailoverLogLevel.Error, msg, Some(e)) => logger.error(msg, e)
    case (FailoverLogLevel.Error, msg, None)    => logger.error(msg)
    case (FailoverLogLevel.Warn, msg, Some(e))  => logger.warn(msg, e)
    case (FailoverLogLevel.Warn, msg, None)     => logger.warn(msg)
    case (FailoverLogLevel.Info, msg, Some(e))  => logger.info(msg, e)
    case (FailoverLogLevel.Info, msg, None)     => logger.info(msg)
    case (FailoverLogLevel.Debug, msg, Some(e)) => logger.debug(msg, e)
    case (FailoverLogLevel.Debug, msg, None)    => logger.debug(msg)
  }

  val retryableByDefault: Throwable => Boolean = {
    case Retryable(_) => true
    case _            => false
  }

  /** A timed-out try and a skipped model are never retried on the same step. */
  val neverRetryByDefault: Throwable => Boolean = {
    case _: OpenAIScalaAttemptTimeoutException => true
    case _: OpenAIScalaModelSkippedException   => true
    case _                                     => false
  }

  /**
   * The time limit of a try: the smaller of the attempt timeout and the time left to the
   * deadline, rounded UP to the millisecond. The time left is in nanoseconds; rounded down,
   * the timer could fire just before the deadline and let one more (billed) try start.
   */
  private[openaiscala] def tryLimit(
    attemptTimeout: Option[FiniteDuration],
    timeLeft: Option[FiniteDuration]
  ): Option[FiniteDuration] = {
    val smaller = (attemptTimeout, timeLeft) match {
      case (Some(a), Some(b)) => Some(a.min(b))
      case (a, b)             => a.orElse(b)
    }

    smaller.map { l =>
      if (l <= Duration.Zero)
        Duration.Zero
      else if (l.toNanos % 1000000L == 0)
        l
      else
        FiniteDuration((l.toNanos + 999999L) / 1000000L, MILLISECONDS)
    }
  }

  /**
   * @param accept
   *   Turns a raw result into the accepted one, or refuses it (a `Left` is re-asked, see
   *   `rejectRetries`). A throw fails the call.
   * @param retrySettings
   *   `maxRetries` = retries of retryable errors per step; the rest = the backoff.
   * @param neverRetry
   *   Errors that are never retried on the same step (they fail over at once).
   * @param rejectRetries
   *   How many times the same step is asked again after a rejected answer (no backoff).
   * @param failoverOnReject
   *   After the re-asks go to the next step, otherwise the rejection error fails the call.
   * @param onOutcome
   *   Sees every try's outcome, late ones included (may be called from other threads).
   * @param wrap
   *   Wraps the raw call of every try (not the timeout race); a synchronous throw counts as
   *   the try's synchronous throw.
   * @param finalError
   *   Picks the error of a run out of steps from each step's final error.
   * @param failureMessage
   *   Used in the log lines.
   * @param labelLines
   *   Whether the retry / re-ask lines are prefixed with the step's label.
   * @param log
   *   Sink of the retry / re-ask lines.
   * @param logFailover
   *   Sink of the failover and final lines (none = no such lines).
   */
  final case class Config[R, E <: Throwable, A](
    accept: R => Either[E, A],
    retrySettings: RetrySettings,
    isRetryable: Throwable => Boolean = retryableByDefault,
    neverRetry: Throwable => Boolean = neverRetryByDefault,
    attemptTimeout: Option[FiniteDuration] = None,
    deadline: Option[Deadline] = None,
    shouldStop: () => Boolean = () => false,
    rejectRetries: Int = 0,
    failoverOnReject: Boolean = false,
    onOutcome: (AttemptInfo, Outcome[R, E]) => Unit = (
      _: AttemptInfo,
      _: Outcome[R, E]
    ) => (),
    wrap: (AttemptInfo, () => AsyncCall[R]) => AsyncCall[R] = (
      _: AttemptInfo,
      call: () => AsyncCall[R]
    ) => call(),
    finalError: Seq[Throwable] => Throwable = (errors: Seq[Throwable]) => errors.last,
    failoverLogLevel: Throwable => FailoverLogLevel = FailoverLogLevel.default,
    failureMessage: Option[String] = None,
    labelLines: Boolean = true,
    includeExceptionMessage: Boolean = false,
    log: Option[String => Unit] = None,
    logFailover: Option[FailoverLog] = None
  )

  private final case class Progress(
    errors: Vector[Throwable], // the final error of every step done so far
    attempts: Int, // tries made so far
    lastError: Option[Throwable] // the most recent error of any try
  )

  /**
   * The engine over Future tries, as a Future: [[runCall]] with each try's Future started when
   * the try starts (a synchronous throw of `call` is the try's synchronous throw - reported
   * `Failed`, never retried), run on `ec`. A try that exceeds its limit runs on, its outcome
   * reported `Late` / `LateFailed`.
   */
  def run[IN, R, E <: Throwable, A](
    steps: Seq[(IN, String)],
    call: IN => Future[R],
    config: Config[R, E, A]
  )(
    implicit ec: ExecutionContext,
    scheduler: AsyncScheduler
  ): Future[A] =
    runCall[IN, R, E, A](steps, futureTry(call), config).toFuture

  /**
   * A try of a Future-based call: the Future starts when the try is built - inside the
   * engine's synchronous-throw guard - and the call waits for it.
   */
  def futureTry[IN, R](call: IN => Future[R]): IN => AsyncCall[R] =
    (input: IN) => {
      val started = call(input)
      AsyncCall.future(started)
    }

  // the failure the time limit of a try fails it with - told apart from the try's own
  // failures by identity
  private final class TryTimedOut(val limit: FiniteDuration)
      extends RuntimeException
      with NoStackTrace

  /**
   * The engine as a lazy call: nothing runs until the call runs, and every run is a new chain
   * of tries. Each try is `call(input)`, built when the try starts (a synchronous throw while
   * building it - or in `config.wrap` - is reported `Failed` and never retried). A try with a
   * limit (`attemptTimeout`, `deadline`) races it: on ZIO and for a native leaf in flight the
   * losing try is interrupted - its request aborted - and reports nothing more; a Future in
   * flight runs on and reports `Late` / `LateFailed`.
   */
  def runCall[IN, R, E <: Throwable, A](
    steps: Seq[(IN, String)],
    call: IN => AsyncCall[R],
    config: Config[R, E, A]
  )(
    implicit scheduler: AsyncScheduler
  ): AsyncCall[A] = {
    require(steps.nonEmpty, "At least one step is required.")

    implicit val retrySettings: RetrySettings = config.retrySettings
    val maxRetries = math.max(config.retrySettings.maxRetries, 0)

    def labelOf(idx: Int): String = steps(idx)._2

    def messagePart(idx: Int): String = {
      val message =
        if (config.labelLines)
          config.failureMessage.map(m => s"${labelOf(idx)} - $m")
        else
          config.failureMessage

      message.map(_.stripSuffix(".") + ". ").getOrElse("")
    }

    def logLine(line: String): Unit = config.log.foreach(_(line))

    def stopped(
      progress: Progress,
      byDeadline: Boolean
    ): AsyncCall[A] = {
      val reason =
        if (byDeadline)
          "the deadline"
        else
          "the stop flag"

      AsyncCall.failed(
        new OpenAIScalaChainStoppedException(
          s"The model failover chain was stopped by $reason after ${progress.attempts} tries.",
          progress.attempts,
          byDeadline,
          progress.lastError.orNull
        )
      )
    }

    def levelOf(error: Throwable): FailoverLogLevel =
      Try(config.failoverLogLevel(error)).toOption
        .filter(_ != null)
        .getOrElse(FailoverLogLevel.Error)

    def finish(progress: Progress): AsyncCall[A] = {
      val errors = progress.errors

      // a throwing (or null-returning) finalError falls back to the last error
      val chosen =
        try
          Option(config.finalError(errors)).getOrElse(errors.last)
        catch {
          case scala.util.control.NonFatal(_) => errors.last
        }

      config.logFailover.foreach(
        _(
          levelOf(chosen),
          s"No more failover inputs to try! Throwing the last error: ${chosen.getMessage}",
          None
        )
      )

      AsyncCall.failed(chosen)
    }

    // records `error` as the final error of the step `idx` and moves on to the next one
    def failOver(
      idx: Int,
      error: Throwable,
      progress: Progress
    ): AsyncCall[A] = {
      config.logFailover.foreach { sink =>
        val next = steps.lift(idx + 1).map(_._2).getOrElse("N/A")

        val msg = config.failureMessage match {
          case Some(m) =>
            s"${labelOf(idx)} - ${m.stripSuffix(".")} after retries! Initiating failover to $next."
          case None =>
            s"${labelOf(idx)} failed after retries! Initiating failover to $next."
        }

        val level = levelOf(error)

        if (level == FailoverLogLevel.Error)
          sink(level, msg, Some(error))
        else
          sink(level, s"$msg Reason: ${error.getMessage}", None)
      }

      runStep(
        idx + 1,
        progress.copy(errors = progress.errors :+ error, lastError = Some(error))
      )
    }

    def runStep(
      idx: Int,
      progress: Progress
    ): AsyncCall[A] =
      if (idx >= steps.size)
        finish(progress)
      else
        attempt(idx, 1, AttemptKind.First, 0, 0, progress)

    def attempt(
      idx: Int,
      tryNumber: Int,
      kind: AttemptKind,
      transientDone: Int,
      rejectDone: Int,
      progress: Progress
    ): AsyncCall[A] = {
      val timeLeft = config.deadline.map(_.timeLeft)

      if (timeLeft.exists(_ <= Duration.Zero))
        stopped(progress, byDeadline = true)
      else
        Try(config.shouldStop()) match {
          case Failure(e)    => AsyncCall.failed(e)
          case Success(true) => stopped(progress, byDeadline = false)
          case Success(false) =>
            launch(
              idx,
              tryNumber,
              kind,
              transientDone,
              rejectDone,
              tryLimit(config.attemptTimeout, timeLeft),
              progress.copy(attempts = progress.attempts + 1)
            )
        }
    }

    def launch(
      idx: Int,
      tryNumber: Int,
      kind: AttemptKind,
      transientDone: Int,
      rejectDone: Int,
      limit: Option[FiniteDuration],
      progress: Progress
    ): AsyncCall[A] = {
      val (input, label) = steps(idx)
      val info = AttemptInfo(label, idx, tryNumber, kind)

      def next(
        newKind: AttemptKind,
        newTransientDone: Int,
        newRejectDone: Int,
        newProgress: Progress
      ): AsyncCall[A] =
        attempt(idx, tryNumber + 1, newKind, newTransientDone, newRejectDone, newProgress)

      def handleFailure(
        e: Throwable,
        syncThrow: Boolean
      ): AsyncCall[A] = {
        val progressNow = progress.copy(lastError = Some(e))

        val retry =
          !syncThrow && !config.neverRetry(e) && transientDone < maxRetries && config
            .isRetryable(e)

        if (retry) {
          val attemptNo = transientDone + 1

          val exceptionMessagePart =
            if (config.includeExceptionMessage)
              s" Error: ${Option(e.getMessage).getOrElse("Unknown error").stripSuffix(".")}."
            else
              ""

          logLine(
            s"${messagePart(idx)}Attempt ${attemptNo}.${exceptionMessagePart} Retrying..."
          )

          AsyncCall.sleep(RetryHelpers.delay(transientDone)).flatMap { _ =>
            next(AttemptKind.TransientRetry, transientDone + 1, rejectDone, progressNow)
          }
        } else
          failOver(idx, e, progressNow)
      }

      def handleResponse(response: R): AsyncCall[A] =
        Try(config.accept(response)) match {
          case Failure(e) => AsyncCall.failed(e)

          case Success(Right(value)) =>
            config.onOutcome(info, Outcome.Accepted(response))
            AsyncCall.successful(value)

          case Success(Left(rejection)) =>
            config.onOutcome(info, Outcome.Rejected(response, rejection))

            val progressNow = progress.copy(lastError = Some(rejection))

            if (rejectDone < config.rejectRetries) {
              logLine(
                s"${messagePart(idx)}Unparseable answer, asking again (re-ask ${rejectDone + 1} of ${config.rejectRetries})."
              )
              next(AttemptKind.ParseReask, transientDone, rejectDone + 1, progressNow)
            } else if (config.failoverOnReject)
              failOver(idx, rejection, progressNow)
            else
              AsyncCall.failed(rejection)
        }

      def reportLate(outcome: Try[R]): Unit =
        outcome match {
          case Success(r) => config.onOutcome(info, Outcome.Late(r))
          case Failure(e) => config.onOutcome(info, Outcome.LateFailed(e))
        }

      Try(config.wrap(info, () => call(input))) match {
        case Failure(e) =>
          config.onOutcome(info, Outcome.Failed(e))
          handleFailure(e, syncThrow = true)

        case Success(raw) =>
          val timedOut = limit.map(new TryTimedOut(_))

          val limited = timedOut match {
            case None =>
              raw

            case Some(marker) =>
              val l = marker.limit

              raw.timeout(
                l,
                marker,
                onLate = reportLate,
                // the call is already running: e.g. a limit beyond the scheduler's maximum
                // delay (an actor system's scheduler adapted via ActorSchedulers takes ~248
                // days at most with Akka's default tick) or a scheduler shut down - the try
                // runs without a limit rather than its outcome being lost
                onUnscheduled = e =>
                  logLine(
                    s"$label - the time limit of $l could not be scheduled (${e.getMessage}); the try runs without one."
                  )
              )
          }

          limited.transformWith {
            case Failure(e) if timedOut.exists(_ eq e) =>
              val l = timedOut.get.limit
              config.onOutcome(info, Outcome.TimedOut(l))

              handleFailure(
                new OpenAIScalaAttemptTimeoutException(
                  s"$label did not answer within $l.",
                  label,
                  l
                ),
                syncThrow = false
              )

            case Success(response) =>
              handleResponse(response)

            case Failure(e) =>
              config.onOutcome(info, Outcome.Failed(e))
              handleFailure(e, syncThrow = false)
          }
      }
    }

    AsyncCall.defer(runStep(0, Progress(Vector.empty, 0, None)))
  }
}
