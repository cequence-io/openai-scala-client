package io.cequence.openaiscala.service.adapter

import scala.concurrent.Future

trait ServiceWrapper[S] extends S {

  protected def wrap[T](
    fun: S => Future[T]
  ): Future[T]
}
