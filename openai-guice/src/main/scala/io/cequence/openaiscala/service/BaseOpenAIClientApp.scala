package io.cequence.openaiscala.service

import com.google.inject.Module

import java.util.concurrent.CountDownLatch
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

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
     * Once the future completes, closes the service and the container's thread pool and exits
     * the JVM: 0 on success, 1 (stack trace printed) on a failure. It returns at once and a
     * non-daemon thread waits instead: the pool's threads and those of the default HTTP engine
     * are daemons, so the JVM would end with the `App` body otherwise - and the body must not
     * block, since on Scala 3 it runs in its object's initializer, which a callback touching
     * the object would wait for.
     */
    def closeAndExit(): Unit = {
      val completed = new CountDownLatch(1)
      f.onComplete(_ => completed.countDown())

      val exitWhenDone: Runnable = () => {
        completed.await()
        val exitCode = f.value match {
          case Some(Failure(e)) =>
            e.printStackTrace()
            1
          case _ =>
            0
        }
        openAIService.close()
        terminate()
        System.exit(exitCode)
      }

      val waiter = new Thread(exitWhenDone, "openai-scala-guice-close-and-exit")
      waiter.setDaemon(false) // keeps the JVM running until the future completes
      waiter.start()
    }
  }
}
