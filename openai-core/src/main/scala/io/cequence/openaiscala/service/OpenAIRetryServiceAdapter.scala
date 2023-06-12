package io.cequence.openaiscala.service

import io.cequence.openaiscala.{OpenAIScalaTokenCountExceededException, StackWalkerUtil}

import scala.concurrent.{ExecutionContext, Future}

private class OpenAIRetryServiceAdapter(
  underlying: OpenAIService,
  maxAttempts: Int,
  sleepOnFailureMs: Option[Int] = None,
  log: String => Unit = println)(
  implicit ec: ExecutionContext
) extends OpenAIServiceWrapper {

  override protected def wrap[T](
    fun: OpenAIService => Future[T]
  ): Future[T] = {
    // need to use StackWalker to get the caller function name
    fun.toString()
    val functionName = StackWalkerUtil.functionName(2).get()
    retry(s"${functionName.capitalize} call failed")(
      fun(underlying)
    )
  }

  override def close(): Unit =
    underlying.close()

  private def retry[T](
    failureMessage: String)(
    f: => Future[T]
  ): Future[T] = {
    def retryAux(attempt: Int): Future[T] =
      f.recoverWith {
        // (re)throw the token count exception... doesn't make sense to retry
        case e: OpenAIScalaTokenCountExceededException =>
          log(s"${failureMessage}. ${e.getMessage}. Propagating further.")
          throw e
        case e: Exception =>
          if (attempt < maxAttempts) {
            log(s"${failureMessage}. ${e.getMessage}. Attempt ${attempt}. Retrying...")

            sleepOnFailureMs.foreach(
              Thread.sleep(_)
            )

            retryAux(attempt + 1)
          } else
            throw e
      }

    retryAux(1)
  }
}


object OpenAIRetryServiceAdapter {
  def apply(
    underlying: OpenAIService,
    maxAttempts: Int,
    sleepOnFailureMs: Option[Int] = None,
    log: String => Unit = println)(
    implicit ec: ExecutionContext
  ): OpenAIService =
    new OpenAIRetryServiceAdapter(
      underlying,
      maxAttempts,
      sleepOnFailureMs,
      log
    )
}