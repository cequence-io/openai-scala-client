package io.cequence.openaiscala.examples

object RetrieveThreadMessage extends Example {

  override protected def run =
    for {
      thread <- service.retrieveThreadMessage(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMx",
        messageId = "msg_MQwpq7pnbvim7UYH4W1b6n5Q"
      )
    } yield {
      println(thread)
    }
}
