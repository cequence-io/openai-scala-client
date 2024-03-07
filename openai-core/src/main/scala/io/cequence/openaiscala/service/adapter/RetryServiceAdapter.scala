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
) extends ServiceWrapper[S] with CloseableService
  with RetryHelpers {

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = {
    // need to use StackWalker to get the caller function name
    val functionName = StackWalkerUtil.functionName(2, Optional.of[Predicate[String]]((t: String) =>
      !t.contains("wrap") && !t.contains("anonfun")
    ))
    fun(underlying).retryOnFailure(
      Some(s"${functionName.orElse("N.A").capitalize} call failed"),
      log
    )
  }

  override def close(): Unit =
    underlying.close()
}
