package io.cequence.openaiscala.service.adapter

import akka.actor.Scheduler
import io.cequence.openaiscala.{RetryHelpers, StackWalkerUtil}
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.service.CloseableService

import java.util.Optional
import java.util.function.Predicate
import scala.concurrent.{ExecutionContext, Future}

private class RetryServiceAdapter[+S <: CloseableService](
  underlying: S,
  log: Option[String => Unit] = None
)(
  implicit ec: ExecutionContext,
  retrySettings: RetrySettings,
  scheduler: Scheduler
) extends ServiceWrapper[S]
    with CloseableService
    with FunctionNameHelper
    with RetryHelpers {

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] =
    fun(underlying).retryOnFailure(
      Some(s"${getFunctionName().capitalize} call failed"),
      log
    )

  override def close(): Unit =
    underlying.close()
}
