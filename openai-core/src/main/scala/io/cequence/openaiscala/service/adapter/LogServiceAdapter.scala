package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.StackWalkerUtil
import io.cequence.wsclient.service.CloseableService

import java.util.Optional
import java.util.function.Predicate
import scala.concurrent.Future

private class LogServiceAdapter[+S <: CloseableService](
  underlying: S,
  serviceName: String,
  log: String => Unit
) extends ServiceWrapper[S]
    with FunctionNameHelper
    with CloseableService {

  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = {
    log(s"${serviceName} - calling '${getFunctionName()}'")
    fun(underlying)
  }

  override def close(): Unit =
    underlying.close()
}

trait FunctionNameHelper {

  private val ignoreFunNames = Seq("wrap", "anonfun", "getFunctionName")

  protected def getFunctionName(): String = {
    // need to use StackWalker to get the caller function name
    val predicate =
      Optional.of[Predicate[String]]((t: String) => ignoreFunNames.forall(!t.contains(_)))

    StackWalkerUtil.functionName(2, predicate).orElse("N/A")
  }
}
