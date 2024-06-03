package io.cequence.openaiscala.examples

import scala.concurrent.Future
object DeleteThread extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.deleteThread(
        "thread_r0uAuizlDYmx9de3pFVwuFsZ"
      )
    } yield {
      println(thread)
    }
}
