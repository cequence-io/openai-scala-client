package io.cequence.openaiscala.examples

object CancelBatch extends Example {

  override protected def run =
    service.cancelBatch("batch_c6fFMmUw30l30SzG2KdUViMn").map(println)
}
