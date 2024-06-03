package io.cequence.openaiscala.examples

import scala.concurrent.Future
object DeleteAssistant extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.deleteAssistant(
        "asst_xxx"
      )
    } yield {
      println(thread)
    }
}
