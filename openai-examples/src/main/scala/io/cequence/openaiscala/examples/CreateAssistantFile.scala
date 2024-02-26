package io.cequence.openaiscala.examples

import scala.concurrent.Future

object CreateAssistantFile extends Example {

  override protected def run: Future[_] =
    for {
      assistantFile <- service.createAssistantFile(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8",
        fileId = "file"
      )
    } yield println(assistantFile)

}
