package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveAssistantFile extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveAssistantFile(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8",
        fileId = "file_2bX"
      )
    } yield {
      println(assistant)
    }

}
