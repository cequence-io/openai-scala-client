package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{CloseableService, OpenAIService, OpenAIServiceFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Example extends ExampleBase[OpenAIService] {
  override protected val service = OpenAIServiceFactory()
}

trait ExampleBase[T <: CloseableService] {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  protected val service: T

  def main(args: Array[String]): Unit = {
    run.recover { case e: Exception =>
      e.printStackTrace()
      closeAll()
      System.exit(1)
    }.onComplete { _ =>
      closeAll()
      System.exit(0)
    }
  }

  private def closeAll() = {
    service.close()
    system.terminate()
  }

  protected def run: Future[_]

  protected def printMessageContent(response: ChatCompletionResponse) =
    println(response.choices.head.message.content)
}
