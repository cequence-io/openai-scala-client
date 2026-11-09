package io.cequence.openaiscala

import io.cequence.wsclient.concurrent.AsyncScheduler
import io.cequence.openaiscala.RetryHelpers.{RetrySettings, retry}
import io.cequence.openaiscala.service.FailoverLogLevel
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object RetryHelpers {
  private val random = new Random()

  private[openaiscala] def delay(
    n: Integer
  )(
    implicit retrySettings: RetrySettings
  ): FiniteDuration = {
    val baseDelay = scala.math.round(
      retrySettings.delayOffset.length + scala.math.pow(
        retrySettings.delayBase,
        n.doubleValue()
      )
    )
    val jitter = retrySettings.jitterMs.map(max => random.nextInt(max)).getOrElse(0)
    FiniteDuration(baseDelay, retrySettings.delayOffset.unit) + FiniteDuration(
      jitter,
      scala.concurrent.duration.MILLISECONDS
    )
  }

  private[openaiscala] def retry[T](
    fun: () => Future[T],
    maxAttempts: Int,
    failureMessage: Option[String] = None,
    log: Option[String => Unit] = Some(println),
    isRetryable: Throwable => Boolean = {
      case Retryable(_) => true
      case _            => false
    },
    includeExceptionMessage: Boolean = false
  )(
    implicit ec: ExecutionContext,
    scheduler: AsyncScheduler,
    retrySettings: RetrySettings
  ): Future[T] = {
    RetryEngine.run[Unit, T, Nothing, T](
      Seq(((), "")),
      _ => fun(),
      RetryEngine.Config[T, Nothing, T](
        accept = (r: T) => Right(r),
        // the count comes from maxAttempts, only the backoff from the settings (as before)
        retrySettings = retrySettings.copy(maxRetries = maxAttempts - 1),
        isRetryable = isRetryable,
        neverRetry = (_: Throwable) => false,
        failureMessage = failureMessage,
        labelLines = false,
        includeExceptionMessage = includeExceptionMessage,
        log = log
      )
    )
  }

  final case class RetrySettings(
    maxRetries: Int = 5,
    delayOffset: FiniteDuration = 2.seconds,
    delayBase: Double = 2,
    jitterMs: Option[Int] = None
  ) {
    def constantInterval(interval: FiniteDuration): RetrySettings =
      copy(delayBase = 0).copy(delayOffset = interval)
  }

  object RetrySettings {
    def apply(interval: FiniteDuration): RetrySettings =
      RetrySettings().constantInterval(
        interval
      )
  }
}

trait RetryHelpers {

  private val logger = LoggerFactory.getLogger(this.getClass)

  implicit class FutureWithRetry[T](op: => Future[T]) {

    def retryOnFailure(
      failureMessage: Option[String] = None,
      log: Option[String => Unit] = Some(println),
      isRetryable: Throwable => Boolean = {
        case Retryable(_) => true
        case _            => false
      },
      includeExceptionMessage: Boolean = false
    )(
      implicit retrySettings: RetrySettings,
      ec: ExecutionContext,
      scheduler: AsyncScheduler
    ): Future[T] = {
      retry(
        () => op,
        maxAttempts = retrySettings.maxRetries + 1,
        failureMessage,
        log,
        isRetryable,
        includeExceptionMessage
      )
    }
  }

  implicit class FutureWithFailover[IN, T](
    f: IN => Future[T]
  ) {
    def retryOnFailureOrFailover(
      normalAndFailoverInputsAndMessages: Seq[(IN, String)], // input and string for logging
      failureMessage: Option[String] = None,
      log: Option[String => Unit] = Some(println),
      isRetryable: Throwable => Boolean = {
        case Retryable(_) => true
        case _            => false
      },
      includeExceptionMessage: Boolean = false,
      failoverLogLevel: Throwable => FailoverLogLevel = FailoverLogLevel.default
    )(
      implicit retrySettings: RetrySettings,
      ec: ExecutionContext,
      scheduler: AsyncScheduler
    ): Future[T] = {
      assert(
        normalAndFailoverInputsAndMessages.nonEmpty,
        "At least one input or last exception must be defined!"
      )

      RetryEngine.run[IN, T, Nothing, T](
        normalAndFailoverInputsAndMessages,
        f,
        RetryEngine.Config[T, Nothing, T](
          accept = (r: T) => Right(r),
          retrySettings = retrySettings,
          isRetryable = isRetryable,
          failoverLogLevel = failoverLogLevel,
          failureMessage = failureMessage,
          includeExceptionMessage = includeExceptionMessage,
          log = log,
          logFailover = Some(RetryEngine.slf4jFailoverLog(logger))
        )
      )
    }
  }
}
