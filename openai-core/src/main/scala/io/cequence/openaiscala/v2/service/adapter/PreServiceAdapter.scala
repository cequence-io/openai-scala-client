package io.cequence.openaiscala.v2.service.adapter

import io.cequence.openaiscala.v2.service

import scala.concurrent.{ExecutionContext, Future}

private class PreServiceAdapter[+S <: service.CloseableService](
  underlying: S,
  action: () => Future[Unit]
)(
  implicit ec: ExecutionContext
) extends ServiceWrapper[S]
    with FunctionNameHelper
    with service.CloseableService {

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] =
    action().flatMap(_ => fun(underlying))

  override def close(): Unit =
    underlying.close()
}
