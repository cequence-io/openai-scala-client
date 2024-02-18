package io.cequence.openaiscala.examples
import io.cequence.openaiscala.domain.Pagination

import scala.concurrent.Future

object ListAssistantFiles extends Example {

  override protected def run: Future[_] =
    for {
      assistantFiles <- service.listAssistantFiles(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8",
        pagination = Pagination.limit(5)
      )
    } yield {
      assistantFiles.foreach(println)
    }
}
