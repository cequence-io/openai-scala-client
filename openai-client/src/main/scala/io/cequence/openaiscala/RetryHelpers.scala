package io.cequence.openaiscala

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.{RetrySupport, after}
import akka.stream.Materializer
import io.cequence.openaiscala.RetryHelpers.RetrySettings

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object RetryHelpers {
  final case class RetrySettings(
      maxRetries: Integer = 5,
      delayBase: FiniteDuration = 2.seconds,
      delayExponent: FiniteDuration = 2.seconds
  )
}

trait RetryHelpers extends RetrySupport {

  val actorSystem: ActorSystem
  implicit val materializer: Materializer = Materializer(actorSystem)

  implicit class FutureWithRetry[T](f: Future[T]) {

    def delay(implicit retrySettings: RetrySettings): FiniteDuration =
      retrySettings.delayBase + scala.math.pow(
        2.0,
        retrySettings.delayExponent.asInstanceOf[Double]
      )

    def retry(
        attempt: () => Future[T],
        attempts: Int
    )(implicit
        ec: ExecutionContext,
        scheduler: Scheduler,
        retrySettings: RetrySettings
    ): Future[T] = {
      try {
        if (attempts > 0) {
          attempt().recoverWith { case Retryable(_) =>
            after(delay, scheduler) {
              retry(attempt, attempts - 1)
            }
          }
        } else {
          attempt()
        }
      } catch {
        case NonFatal(error) => Future.failed(error)
      }
    }

    def retryOnFailure(implicit
        retrySettings: RetrySettings,
        ec: ExecutionContext,
        scheduler: Scheduler
    ): Future[T] = {
      retry(() => f, retrySettings.maxRetries)
    }
  }
}
