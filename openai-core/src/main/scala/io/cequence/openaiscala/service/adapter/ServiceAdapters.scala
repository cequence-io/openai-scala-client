package io.cequence.openaiscala.service.adapter

import io.cequence.wsclient.concurrent.AsyncScheduler
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.Retryable
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.{ParallelServiceAdapters, ServiceBaseAdapters}

import scala.concurrent.ExecutionContext

trait ServiceAdapters[S <: CloseableService]
    extends ServiceBaseAdapters[S]
    with ParallelServiceAdapters[S] {

  // TODO: move to ServiceBaseAdapters
  def retry(
    underlying: S,
    log: Option[String => Unit] = None,
    isRetryable: Throwable => Boolean = {
      case Retryable(_) => true
      case _            => false
    },
    includeExceptionMessage: Boolean = false
  )(
    implicit ec: ExecutionContext,
    retrySettings: RetrySettings,
    scheduler: AsyncScheduler
  ): S =
    wrapAndDelegate(
      new RetryServiceAdapter(underlying, log, isRetryable, includeExceptionMessage)
    )
}
