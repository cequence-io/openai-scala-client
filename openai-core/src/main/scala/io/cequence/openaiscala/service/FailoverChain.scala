package io.cequence.openaiscala.service

import io.cequence.wsclient.concurrent.AsyncScheduler
import io.cequence.wsclient.stream.AsyncCall
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
 * `createChatCompletionWithJSON*`, driven by a [[FailoverPolicy]]. One lazy call
 * ([[chainCall]]): the Future helpers run it over the service's Future method, the call
 * helpers over its call twin - native on ZIO, each try interruptible.
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
    scheduler: AsyncScheduler
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
    scheduler: AsyncScheduler
  ): Future[A] =
    chainCall(
      service,
      steps,
      accept,
      policy,
      retrySettings,
      isRetryable,
      failureMessage,
      logger,
      futureTriesOn = Some(ec)
    ).toFuture

  /**
   * The chain as a lazy call (since 2.1.0).
   *
   * @param futureTriesOn
   *   `Some(ec)`: each try is the service's Future `createChatCompletion`, started when the
   *   try starts - a synchronous throw (of the service or of a [[FailoverPolicy.wrapAttempt]])
   *   is the try's synchronous throw, as in the Future helpers. `None`: each try is the
   *   service's `createChatCompletionCall` - native on ZIO, interruptible; a wrapAttempt other
   *   than the identity then sees the try as a Future (one bridge) and its throw is a failed
   *   try (a skip exception is still never retried).
   */
  def chainCall[A](
    service: OpenAIChatCompletionService,
    steps: Seq[LazyStep],
    accept: ChatCompletionResponse => Either[OpenAIScalaJsonParseException, A],
    policy: FailoverPolicy,
    retrySettings: RetrySettings,
    isRetryable: Throwable => Boolean,
    failureMessage: String,
    logger: Logger,
    futureTriesOn: Option[ExecutionContext]
  )(
    implicit scheduler: AsyncScheduler
  ): AsyncCall[A] = {
    val tryOf: LazyStep => AsyncCall[ChatCompletionResponse] = futureTriesOn match {
      case Some(_) =>
        RetryEngine.futureTry[LazyStep, ChatCompletionResponse](lazyStep =>
          service.createChatCompletion(lazyStep.step.messages, lazyStep.step.settings)
        )

      case None =>
        (lazyStep: LazyStep) =>
          service.createChatCompletionCall(lazyStep.step.messages, lazyStep.step.settings)
    }

    chain(
      steps,
      tryOf,
      accept,
      policy,
      retrySettings,
      isRetryable,
      failureMessage,
      logger,
      futureTriesOn
    )
  }

  // the chain; `ec` given = the Future path, where a wrapAttempt runs when the try starts (its
  // throw being the try's synchronous throw, as before 2.1.0)
  private def chain[A](
    steps: Seq[LazyStep],
    tryOf: LazyStep => AsyncCall[ChatCompletionResponse],
    accept: ChatCompletionResponse => Either[OpenAIScalaJsonParseException, A],
    policy: FailoverPolicy,
    retrySettings: RetrySettings,
    isRetryable: Throwable => Boolean,
    failureMessage: String,
    logger: Logger,
    ec: Option[ExecutionContext]
  )(
    implicit scheduler: AsyncScheduler
  ): AsyncCall[A] = {
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

    def wrapped(
      info: AttemptInfo,
      call: () => AsyncCall[ChatCompletionResponse]
    ): AsyncCall[ChatCompletionResponse] =
      if (policy.wrapAttempt eq AttemptWrapper.identity)
        call()
      else
        ec match {
          case Some(executor) =>
            val started = policy.wrapAttempt(info, () => call().toFuture(executor))
            AsyncCall.future(started)

          case None =>
            AsyncCall.fromFuture(executor =>
              policy.wrapAttempt(info, () => call().toFuture(executor))
            )
        }

    RetryEngine.runCall[LazyStep, ChatCompletionResponse, OpenAIScalaJsonParseException, A](
      stepsByIndex.map(step => (step, step.model)),
      tryOf,
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
          wrapped(info, call)
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
