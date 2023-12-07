package io.cequence.openaiscala.examples

object ListThreadMessages extends Example {

  override protected def run =
    for {
      messages <- service.listThreadMessages(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        limit = Some(5)
      )
    } yield {
      messages.foreach(println)
    }
}
