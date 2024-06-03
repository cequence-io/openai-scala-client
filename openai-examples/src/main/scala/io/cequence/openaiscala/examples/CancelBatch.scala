package io.cequence.openaiscala.examples

import scala.concurrent.Future
object CancelBatch extends Example {

  override protected def run: Future[Unit] =
    service.cancelBatch("batch_c6fFMmUw30l30SzG2KdUViMn").map(println)
}
