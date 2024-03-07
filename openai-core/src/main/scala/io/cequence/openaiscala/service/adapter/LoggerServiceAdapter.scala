package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.service.CloseableService
import io.cequence.openaiscala.StackWalkerUtil

import java.util.Optional
import java.util.function.Predicate
import scala.compat.java8.StreamConverters._
import scala.concurrent.Future

private class LogServiceAdapter[+S <: CloseableService](
  underlying: S,
  serviceName: String,
  log: String => Unit
) extends ServiceWrapper[S] with CloseableService {

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = {
    // need to use StackWalker to get the caller function name
    val functionName = StackWalkerUtil.functionName(2, Optional.of[Predicate[String]]((t: String) =>
      !t.contains("wrap") && !t.contains("anonfun")
    ))

    log(s"${serviceName} - calling '${functionName.orElse("N/A")}'")
    fun(underlying)
  }

  override def close(): Unit =
    underlying.close()
}
