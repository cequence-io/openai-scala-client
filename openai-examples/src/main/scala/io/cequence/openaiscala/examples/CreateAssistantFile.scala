package io.cequence.openaiscala.examples
import io.cequence.openaiscala.domain.FileId

import scala.concurrent.Future

object CreateAssistantFile extends Example {

  override protected def run: Future[_] =
    for {
      assistantFile <- service.createAssistantFile(
        assistantId = "assistant-id",
        fileId = "file"
      )
    } yield println(assistantFile)

}
