package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Example {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  val service: OpenAIService = OpenAIServiceFactory() // sys.env("OPENAI_API_KEY")

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
