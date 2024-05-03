package io.cequence.openaiscala.examples

object RetrieveBatch extends Example {

  override protected def run =
    service.retrieveBatch("batch_c6fFMmUw30l30SzG2KdUViMn").map(println)
}
