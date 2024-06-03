package io.cequence.openaiscala.examples

import scala.concurrent.Future
object DeleteAssistant extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.deleteAssistant(
        "asst_Btbc2h7dqyDU52g1KfBa2XE9"
      )
    } yield {
      println(thread)
    }
}
