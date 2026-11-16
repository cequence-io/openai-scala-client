package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.wsclient.service.CloseableService

import java.util.concurrent.ExecutionException
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

trait Example extends ExampleBase[OpenAIService] {
  override protected val service = OpenAIServiceFactory()
}

trait ExampleBase[T <: CloseableService] {

  // what `main` waits for: `run`'s outcome, or the first fatal error (a LinkageError from a
  // mismatched jar, an OutOfMemoryError) thrown in a callback - such an error never completes
  // its future (the promise rethrows it), so waiting for `run` alone would hang
  private val outcome = Promise[Any]()

  // the global pool, with fatal errors in callbacks reported to `outcome`
  implicit val ec: ExecutionContext = new ExecutionContext {
    override def execute(runnable: Runnable): Unit =
      ExecutionContext.global.execute { () =>
        try {
          runnable.run()
        } catch {
          case e: Throwable if !NonFatal(e) =>
            outcome.tryFailure(
              new ExecutionException("A callback failed with a fatal error", e)
            )
            throw e
        }
      }

    override def reportFailure(cause: Throwable): Unit =
      ExecutionContext.global.reportFailure(cause)
  }

  protected val service: T

  // Waits for `run`: the default engine's and the global pool's threads are daemons, so a main
  // that returned first would end the JVM at once - exit code 0, nothing printed.
  def main(args: Array[String]): Unit = {
    val exitCode =
      try {
        Await.result(outcome.completeWith(run).future, Duration.Inf)
        0
      } catch {
        case NonFatal(e) =>
          println("Error received:")
          e.printStackTrace()
          1
      }
    closeAll()
    System.exit(exitCode)
  }

  private def closeAll() = {
    service.close()
  }

  protected def run: Future[_]

  protected def printMessageContent(response: ChatCompletionResponse): Unit =
    println(response.contentHead)
}
