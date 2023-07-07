package io.cequence.openaiscala.service

import akka.actor.Scheduler
import io.cequence.openaiscala.{RetryHelpers, StackWalkerUtil}
import io.cequence.openaiscala.RetryHelpers.RetrySettings

import scala.concurrent.{ExecutionContext, Future}

private class OpenAIRetryServiceAdapter(
  underlying: OpenAIService,
  log: Option[String => Unit] = None)(
  implicit ec: ExecutionContext, retrySettings: RetrySettings, scheduler: Scheduler
) extends OpenAIServiceWrapper
    with RetryHelpers {

  override def close(): Unit =
    underlying.close()

  override protected def wrap[T](
    fun: OpenAIService => Future[T]
  ): Future[T] = {
    // need to use StackWalker to get the caller function name
    val functionName = StackWalkerUtil.functionName(2).get()
    fun(underlying).retryOnFailure(
      Some(s"${functionName.capitalize} call failed"),
      log
    )
  }
}

object OpenAIRetryServiceAdapter {
  def apply(
    underlying: OpenAIService,
    log: Option[String => Unit] = None)(
    implicit ec: ExecutionContext,
    retrySettings: RetrySettings,
    scheduler: Scheduler
  ): OpenAIService =
    new OpenAIRetryServiceAdapter(underlying, log)
}