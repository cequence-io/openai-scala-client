package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

trait Example extends ExampleBase[OpenAIService] {
  override protected val service = OpenAIServiceFactory()
}

trait ExampleBase[T <: CloseableService] {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  protected val service: T

  // Waits for `run`: the default engine's and the global pool's threads are daemons, so a main
  // that returned first would end the JVM at once - exit code 0, nothing printed.
  def main(args: Array[String]): Unit = {
    val exitCode =
      try {
        Await.result(run, Duration.Inf)
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
