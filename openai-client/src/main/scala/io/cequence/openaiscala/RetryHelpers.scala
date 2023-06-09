package io.cequence.openaiscala

import akka.actor.Scheduler
import akka.pattern.RetrySupport
import io.cequence.openaiscala.RetryHelpers.RetrySettings

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object RetryHelpers {
  final case class RetrySettings(
      maxRetries: Integer,
      delay: FiniteDuration
  )
}

trait RetryHelpers extends RetrySupport {

  implicit class FutureWithRetry[T](f: Future[T]) {
    def retryOnFailure(implicit
        retrySettings: RetrySettings,
        ec: ExecutionContext,
        scheduler: Scheduler
    ): Future[T] = {
      retry(() => f, retrySettings.maxRetries, retrySettings.delay)
    }
  }
}
