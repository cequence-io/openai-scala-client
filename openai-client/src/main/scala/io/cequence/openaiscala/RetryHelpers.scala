package io.cequence.openaiscala

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.RetrySupport
import akka.stream.Materializer
import io.cequence.openaiscala.RetryHelpers.RetrySettings

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

object RetryHelpers {
  final case class RetrySettings(
      maxRetries: Integer = 5,
      delay: FiniteDuration = 2.seconds
  )
}

trait RetryHelpers extends RetrySupport {

  val actorSystem: ActorSystem
  implicit val materializer: Materializer = Materializer(actorSystem)

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
