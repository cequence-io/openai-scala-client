package io.cequence.openaiscala.examples

import scala.concurrent.Future

object ListRunSteps extends Example {

  override protected def run: Future[Unit] =
    service.listRunSteps("thread_xxx", "run_xxx").map(x => println(x))
}
