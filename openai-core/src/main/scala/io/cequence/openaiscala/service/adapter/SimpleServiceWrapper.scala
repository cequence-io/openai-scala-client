package io.cequence.openaiscala.service.adapter

import io.cequence.wsclient.service.CloseableService
import ServiceWrapperTypes.CloseableServiceWrapper

import scala.concurrent.Future

object SimpleServiceWrapper {

  def apply[S <: CloseableService](
    service: S
  ): CloseableServiceWrapper[S] =
    new SimpleServiceWrapper(service)

  private final class SimpleServiceWrapper[S <: CloseableService](
    service: S
  ) extends ServiceWrapper[S]
      with CloseableService {

    override protected[adapter] def wrap[T](
      fun: S => Future[T]
    ): Future[T] =
      fun(service)

    override def close(): Unit =
      service.close()
  }
}
