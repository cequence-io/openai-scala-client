package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Pagination

import scala.concurrent.Future

object ListThreads extends Example {

  override protected def run: Future[Unit] =
    for {
      messages <- service.listThreadMessages(
        threadId = "thread_xxx",
        Pagination.limit(5)
      )
    } yield {
      messages.foreach(println)
    }
}
