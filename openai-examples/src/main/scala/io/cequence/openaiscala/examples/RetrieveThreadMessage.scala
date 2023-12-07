package io.cequence.openaiscala.examples

object RetrieveThreadMessage extends Example {

  override protected def run =
    for {
      thread <- service.retrieveThreadMessage(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        messageId = "msg_DL89m3TK5i8su3vqFv0qqJTP"
      )
    } yield {
      println(thread)
    }
}
