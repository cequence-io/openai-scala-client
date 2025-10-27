package io.cequence.openaiscala.service.adapter

import io.cequence.wsclient.service.CloseableService

import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

// TODO: move to ws client
private final class RepackExceptionsAdapter[+S <: CloseableService](
  underlying: S,
  repackExceptions: PartialFunction[Throwable, Throwable]
)(
  implicit ec: ExecutionContext
) extends ServiceWrapper[S]
    with CloseableService {

  override def wrap[T](fun: S => Future[T]): Future[T] = {
    // Ensure sync exceptions are turned into a failed Future
    val fut: Future[T] =
      try fun(underlying)
      catch { case NonFatal(t) => Future.failed(t) }

    fut.recoverWith {
      case e if repackExceptions.isDefinedAt(e) =>
        // Be defensive in case repackExceptions itself throws
        try {
          val repacked = repackExceptions(e)
          // If the repacked throwable doesn’t chain the cause, attach it
          // if (repacked.getCause eq null) repacked.initCause(e)
          Future.failed(repacked)
        } catch {
          case NonFatal(thrownDuringRepack) =>
            thrownDuringRepack.addSuppressed(e)
            Future.failed(thrownDuringRepack)
        }
    }
  }

  override def close(): Unit = underlying.close()
}
