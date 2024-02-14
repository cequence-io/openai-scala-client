package io.cequence.openaiscala.examples
import scala.concurrent.Future

object ListAssistantFiles extends Example {

  override protected def run: Future[_] =
    for {
      assistantFiles <- service.listAssistantFiles(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8",
        limit = Some(5)
      )
    } yield {
      assistantFiles.foreach(println)
    }
}
