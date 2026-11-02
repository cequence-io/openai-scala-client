package io.cequence.openaiscala.service

import akka.actor.Scheduler
import io.cequence.openaiscala.RetryEngine.Outcome
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{OpenAIScalaJsonParseException, RetryEngine}
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * The model failover chain behind `createChatCompletionWithFailover*` and
 * `createChatCompletionWithJSON*`, driven by a [[FailoverPolicy]]. Non-blocking: every step is
 * a Future continuation.
 */
private[openaiscala] object FailoverChain {

  final case class Step(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  )

  /**
   * A step whose request is built when its first try starts, then reused by its retries and
   * re-asks - so a failover model that is never reached is never prepared. A step that cannot
   * be built fails its try like a synchronous throw: reported as `Failed`, never retried on
   * the same model, failed over.
   */
  final class LazyStep(
    val model: String,
    build: () => Step
  ) {
    lazy val step: Step = build()
  }

  def run[A](
    service: OpenAIChatCompletionService,
    steps: Seq[Step],
    accept: ChatCompletionResponse => Either[OpenAIScalaJsonParseException, A],
    policy: FailoverPolicy,
    retrySettings: RetrySettings,
    isRetryable: Throwable => Boolean,
    failureMessage: String,
    logger: Logger
  )(
    implicit ec: ExecutionContext,
    scheduler: Scheduler
  ): Future[A] =
    runLazy(
      service,
      steps.map(step => new LazyStep(step.settings.model, () => step)),
      accept,
      policy,
      retrySettings,
      isRetryable,
      failureMessage,
      logger
    )

  def runLazy[A](
    service: OpenAIChatCompletionService,
    steps: Seq[LazyStep],
    accept: ChatCompletionResponse => Either[OpenAIScalaJsonParseException, A],
    policy: FailoverPolicy,
    retrySettings: RetrySettings,
    isRetryable: Throwable => Boolean,
    failureMessage: String,
    logger: Logger
  )(
    implicit ec: ExecutionContext,
    scheduler: Scheduler
  ): Future[A] = {
    require(steps.nonEmpty, "At least one step is required.")

    val stepsByIndex = steps.toVector

    // a throwing listener is logged and ignored
    def notify(
      info: AttemptInfo,
      outcome: AttemptOutcome
    ): Unit =
      try
        policy.onAttempt(info, outcome)
      catch {
        case NonFatal(e) =>
          logger.warn(s"The attempt listener failed (ignored): ${e.getMessage}", e)
      }

    val onOutcome: (
      AttemptInfo,
      Outcome[ChatCompletionResponse, OpenAIScalaJsonParseException]
    ) => Unit = {
      case (info, Outcome.Accepted(r))    => notify(info, AttemptOutcome.Answered(r))
      case (info, Outcome.Rejected(r, e)) => notify(info, AttemptOutcome.Unparseable(r, e))
      case (info, Outcome.Failed(e))      => notify(info, AttemptOutcome.Failed(e))
      case (info, Outcome.TimedOut(l))    => notify(info, AttemptOutcome.TimedOut(l))
      case (info, Outcome.Late(r))        => notify(info, AttemptOutcome.LateAnswer(r))
      case (info, Outcome.LateFailed(e))  => notify(info, AttemptOutcome.LateFailure(e))
    }

    RetryEngine.run[LazyStep, ChatCompletionResponse, OpenAIScalaJsonParseException, A](
      stepsByIndex.map(step => (step, step.model)),
      lazyStep => service.createChatCompletion(lazyStep.step.messages, lazyStep.step.settings),
      RetryEngine.Config[ChatCompletionResponse, OpenAIScalaJsonParseException, A](
        accept = response => accept(response),
        retrySettings = retrySettings,
        isRetryable = isRetryable,
        attemptTimeout = policy.attemptTimeout,
        deadline = policy.deadline,
        shouldStop = policy.shouldStop,
        rejectRetries = policy.parseRetries,
        failoverOnReject = policy.failoverOnParseError,
        onOutcome = onOutcome,
        wrap = (
          info,
          call
        ) => {
          // the step is built before the policy's wrapper sees the try: a build failure throws
          // here, synchronously - so the engine reports it, never retries it and fails over
          stepsByIndex(info.modelIndex).step
          policy.wrapAttempt(info, call)
        },
        finalError = policy.finalError,
        failoverLogLevel = policy.failoverLogLevel,
        failureMessage = Some(failureMessage),
        includeExceptionMessage = true,
        log = Some((line: String) => logger.warn(line)),
        logFailover = Some(RetryEngine.slf4jFailoverLog(logger))
      )
    )
  }
}
