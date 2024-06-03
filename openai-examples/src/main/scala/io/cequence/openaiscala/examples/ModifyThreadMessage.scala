package io.cequence.openaiscala.examples

import scala.concurrent.Future
object ModifyThreadMessage extends Example {

  override protected def run: Future[Unit] =
    for {
      message <- service.modifyThreadMessage(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        messageId = "msg_DL89m3TK5i8su3vqFv0qqJTP",
        metadata = Map("user_id" -> "986419", "due_date" -> "2028-08-01")
      )
    } yield {
      println(message)
    }
}
