package io.cequence.openaiscala.anthropic.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}

import scala.concurrent.{ExecutionContext, Future}

trait Example extends ExampleBase[AnthropicService] {
  override protected val service = AnthropicServiceFactory()
}

trait ExampleBase[T <: AnthropicService] {

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

  protected def printMessageContent(response: CreateMessageResponse) =
    response.content.blocks.foreach(println)
}
