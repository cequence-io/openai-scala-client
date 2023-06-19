package io.cequence.openaiscala.service

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.RetryHelpers
import io.cequence.openaiscala.RetryHelpers.RetrySettings

import scala.concurrent.{ExecutionContext, Future}

private class OpenAIRetryServiceAdapter(
    underlying: OpenAIService,
    val actorSystem: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val retrySettings: RetrySettings,
    implicit val scheduler: Scheduler
) extends OpenAIServiceWrapper
    with RetryHelpers {

  override def close: Unit =
    underlying.close

  override protected def wrap[T](
      fun: OpenAIService => Future[T]
  ): Future[T] = {
    fun(underlying).retryOnFailure
  }
}

object OpenAIRetryServiceAdapter {
  def apply(underlying: OpenAIService)(implicit
      ec: ExecutionContext,
      retrySettings: RetrySettings,
      scheduler: Scheduler,
      actorSystem: ActorSystem
  ): OpenAIService =
    new OpenAIRetryServiceAdapter(
      underlying,
      actorSystem,
      ec,
      retrySettings,
      scheduler
    )
}
