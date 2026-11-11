package io.cequence.openaiscala.service

import com.google.inject.Module

import scala.concurrent.{ExecutionContext, Future}
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
    def closeAndExit(): Unit =
      f.recover { case NonFatal(e) =>
        e.printStackTrace()
        System.exit(1)
      }.onComplete { _ =>
        openAIService.close()
        terminate()
        System.exit(0)
      }
  }
}
