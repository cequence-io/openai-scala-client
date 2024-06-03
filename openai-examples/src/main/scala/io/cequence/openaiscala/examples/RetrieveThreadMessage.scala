package io.cequence.openaiscala.examples

import scala.concurrent.Future
object RetrieveThreadMessage extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.retrieveThreadMessage(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        messageId = "msg_DL89m3TK5i8su3vqFv0qqJTP"
      )
    } yield {
      println(thread)
    }
}
