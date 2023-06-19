package io.cequence.openaiscala

import akka.actor.Scheduler
import akka.pattern.after
import io.cequence.openaiscala.RetryHelpers.{RetrySettings, retry}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object RetryHelpers {
  private[openaiscala] def delay(
    n: Integer)(
    implicit retrySettings: RetrySettings
  ): FiniteDuration =
    FiniteDuration(
      scala.math.round(
        retrySettings.delayOffset.length + scala.math.pow(
          retrySettings.delayBase,
          n.doubleValue()
        )
      ),
      retrySettings.delayOffset.unit
    )

  private[openaiscala] def retry[T](
    fun: () => Future[T],
    maxAttempts: Int,
    failureMessage: Option[String] = None,
    log: Option[String => Unit] = Some(println))(
    implicit ec: ExecutionContext,
    scheduler: Scheduler,
    retrySettings: RetrySettings
  ): Future[T] = {
    def retryAux(attempt: Int): Future[T] =
      try {
        if (attempt < maxAttempts) {
          fun().recoverWith { case Retryable(_) =>
            log.foreach(
              _(s"${failureMessage.map(_ + ". ").getOrElse("")}Attempt ${attempt}. Retrying...")
            )

            after(delay(attempt - 1), scheduler) {
              retryAux(attempt + 1)
            }
          }
        } else {
          fun()
        }
      } catch {
        case NonFatal(error) => Future.failed(error)
      }

    retryAux(1)
  }

  final case class RetrySettings(
    maxRetries: Int = 5,
    delayOffset: FiniteDuration = 2.seconds,
    delayBase: Double = 2
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

  implicit class FutureWithRetry[T](f: Future[T]) {

    def retryOnFailure(
      failureMessage: Option[String] = None,
      log: Option[String => Unit] = Some(println))(
      implicit retrySettings: RetrySettings, ec: ExecutionContext, scheduler: Scheduler
    ): Future[T] = {
      retry(() => f, maxAttempts = retrySettings.maxRetries + 1, failureMessage, log)
    }
  }
}