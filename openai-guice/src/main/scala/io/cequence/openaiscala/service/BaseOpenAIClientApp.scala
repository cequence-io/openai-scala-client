package io.cequence.openaiscala.service

import com.google.inject.Module

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

trait BaseOpenAIClientApp extends GuiceContainer with App {

  // modules
  override protected def modules: Seq[Module] = Seq(
    new ConfigModule(),
    new ExecutionContextModule(),
    new ServiceModule()
  )

  protected val openAIService: OpenAIService = instance[OpenAIService]

  // implicits
  protected implicit val executionContext: ExecutionContext = instance[ExecutionContext]

  implicit protected class FutureOps[T](f: Future[T]) {

    /**
     * Waits for the future, closes the service and the container's thread pool, then exits the
     * JVM: 0 on success, 1 (stack trace printed) on a failure. It blocks the calling thread on
     * purpose: the pool's threads and those of the default HTTP engine are daemons, so an
     * `App` body that returned before the future finished would end the JVM at once.
     */
    def closeAndExit(): Unit = {
      val exitCode =
        try {
          Await.result(f, Duration.Inf)
          0
        } catch {
          case NonFatal(e) =>
            e.printStackTrace()
            1
        }
      openAIService.close()
      terminate()
      System.exit(exitCode)
    }
  }
}
