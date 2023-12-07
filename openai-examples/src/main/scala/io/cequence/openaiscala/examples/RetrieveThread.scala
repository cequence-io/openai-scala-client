package io.cequence.openaiscala.examples

object RetrieveThread extends Example {

  override protected def run =
    for {
      thread <- service.retrieveThread(
        "thread_c6fFMmUw30l30SzG2KdUViMn"
      )
    } yield {
      println(thread)
    }
}
