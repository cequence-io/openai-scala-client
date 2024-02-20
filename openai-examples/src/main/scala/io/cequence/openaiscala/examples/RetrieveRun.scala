package io.cequence.openaiscala.examples

object RetrieveRun extends Example {

  override protected def run =
    for {
      thread <- service.retrieveRun(
        "thread_c6fFMmUw30l30SzG2KdUViMn",
        "run_1"
      )
    } yield {
      println(thread)
    }
}
