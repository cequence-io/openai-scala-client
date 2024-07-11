package io.cequence.openaiscala.examples

import scala.concurrent.{ExecutionContext, Future}

trait PollingHelper {

  protected val pollingMs = 200

  protected def pollUntilDone[T](
    isDone: T => Boolean
  )(
    call: => Future[T]
  )(
    implicit ec: ExecutionContext
  ): Future[T] =
    call.flatMap(result =>
      if (isDone(result)) {
        Future(result)
      } else {
        java.lang.Thread.sleep(pollingMs) // TODO: use scheduler
        pollUntilDone(isDone)(call)
      }
    )
}
