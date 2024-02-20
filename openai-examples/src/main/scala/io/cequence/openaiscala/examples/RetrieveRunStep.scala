package io.cequence.openaiscala.examples

object RetrieveRunStep extends Example {

  override protected def run =
    for {
      thread <- service.retrieveRunStep(
        "thread_c6fFMmUw30l30SzG2KdUViMn",
        "run_1",
        "step_1"
      )
    } yield {
      println(thread)
    }
}
