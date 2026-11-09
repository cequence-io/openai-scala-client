package io.cequence.openaiscala.service.adapter

import io.cequence.wsclient.concurrent.AsyncScheduler
import io.cequence.openaiscala.RetryHelpers
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.{FunctionNameHelper, ServiceWrapper}

import scala.concurrent.{ExecutionContext, Future}

private class RetryServiceAdapter[+S <: CloseableService](
  underlying: S,
  log: Option[String => Unit] = None,
  isRetryable: Throwable => Boolean,
  includeExceptionMessage: Boolean = false
)(
  implicit ec: ExecutionContext,
  retrySettings: RetrySettings,
  scheduler: AsyncScheduler
) extends ServiceWrapper[S]
    with CloseableService
    with FunctionNameHelper
    with RetryHelpers {

  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] =
    fun(underlying).retryOnFailure(
      Some(s"${getFunctionName().capitalize} call failed"),
      log,
      isRetryable,
      includeExceptionMessage
    )

  override def close(): Unit =
    underlying.close()
}
