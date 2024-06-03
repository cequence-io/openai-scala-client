package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Pagination
import scala.concurrent.Future

object ListThreadMessages extends Example {

  override protected def run: Future[Unit] =
    for {
      messages <- service.listThreadMessages(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        Pagination.limit(5)
      )
    } yield {
      messages.foreach(println)
    }
}
