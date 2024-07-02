package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Pagination

import scala.concurrent.Future

object ListAssistants extends Example {

  override protected def run: Future[_] =
    for {
      assistants <- service.listAssistants(Pagination.limit(100))
    } yield {
      assistants.foreach(println)
    }

}
