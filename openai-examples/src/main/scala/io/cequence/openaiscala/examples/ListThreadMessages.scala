package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Pagination
import scala.concurrent.Future

object ListThreadMessages extends Example {

  override protected def run: Future[Unit] =
    for {
      messages <- service.listThreadMessages(
//        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        threadId = "thread_q5LqRp5snpGIG7yxZvUop2rj",
        Pagination.limit(5)
      )
    } yield {
      messages.foreach(println)
    }
}
