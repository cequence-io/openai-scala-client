package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Pagination

import scala.concurrent.Future

object ListBatches extends Example {

  override protected def run: Future[_] =
    for {
      assistants <- service.listBatches(Pagination.limit(5))
    } yield {
      if (assistants.isEmpty) println("No batches found")
      else assistants.foreach(println)
    }

}
