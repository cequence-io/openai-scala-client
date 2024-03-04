package io.cequence.openaiscala.examples

object CancelRun extends Example {

  override protected def run =
    for {
      thread <- service.cancelRun(
        "thread_r0uAuizlDYmx9de3pFVwuFsZ",
        "run_1"
      )
    } yield {
      println(thread)
    }
}
