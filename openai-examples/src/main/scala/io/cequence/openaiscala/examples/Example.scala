package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Example[T] {

  implicit val materializer: Materializer = Materializer(ActorSystem())
  val service: OpenAIService = OpenAIServiceFactory(sys.env("OPENAI_API_KEY"))

  def main(args: Array[String]): Unit = {
    val f = example
    f.recover { case e: OpenAIScalaClientException =>
      e.printStackTrace()
    }
    f.onComplete(_ => service.close())
  }

  def example: Future[T]

}
