package io.cequence.openaiscala.examples

import scala.concurrent.Future

object ListRunSteps extends Example {

  override protected def run: Future[Unit] =
    service
      .listRunSteps("thread_YnAy1CQ51AkBmcI3XWO1zuUB", "run_teGlp0JtYh1QaFk73FtuHMAz")
      .map(x => println(x))
}
