package io.cequence.openaiscala.service.adapter

import akka.actor.Scheduler
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.Retryable
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceBaseAdapters

import scala.concurrent.{ExecutionContext, Future}

trait ServiceAdapters[S <: CloseableService] extends ServiceBaseAdapters[S] {

  // TODO: move to ServiceBaseAdapters
  def retry(
    underlying: S,
    log: Option[String => Unit] = None,
    isRetryable: Throwable => Boolean = {
      case Retryable(_) => true
      case _            => false
    }
  )(
    implicit ec: ExecutionContext,
    retrySettings: RetrySettings,
    scheduler: Scheduler
  ): S =
    wrapAndDelegate(new RetryServiceAdapter(underlying, log, isRetryable))
}
