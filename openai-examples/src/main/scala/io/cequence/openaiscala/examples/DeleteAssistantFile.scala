package io.cequence.openaiscala.examples

import scala.concurrent.Future
object DeleteAssistantFile extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.deleteAssistantFile(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8",
        fileId = "file_0123"
      )
    } yield {
      println(thread)
    }
}
