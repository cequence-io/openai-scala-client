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
      delayExponent: Double = 2
  )
}

trait RetryHelpers extends RetrySupport {

  def actorSystem: ActorSystem
  implicit val materializer: Materializer = Materializer(actorSystem)

  implicit class FutureWithRetry[T](f: Future[T]) {

    def delay(
        n: Integer
    )(implicit retrySettings: RetrySettings): FiniteDuration =
      FiniteDuration(
        scala.math.round(
          retrySettings.delayBase.length + scala.math.pow(
            retrySettings.delayExponent,
            n.doubleValue()
          )
        ),
        retrySettings.delayBase.unit
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
            after(delay(attempts), scheduler) {
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
