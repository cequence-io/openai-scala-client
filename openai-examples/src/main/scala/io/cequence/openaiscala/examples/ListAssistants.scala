package io.cequence.openaiscala.examples
import scala.concurrent.Future

object ListAssistants extends Example {

  override protected def run: Future[_] =
    for {
      assistants <- service.listAssistants(limit = Some(5))
    } yield {
      assistants.foreach(println)
    }

}
