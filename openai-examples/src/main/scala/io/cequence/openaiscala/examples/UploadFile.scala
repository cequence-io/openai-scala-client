package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.FileUploadPurpose

import java.io.File
import java.nio.file.Paths
import scala.concurrent.Future

object UploadFile extends Example {

  private def scheduleFile(): File =
    Paths
      .get(
        "/Users/boris/proj/cequence/_source/openai-scala-client/openai-examples/src/main/resources/CRA.txt"
      )
      .toFile

  override protected def run: Future[_] =
    for {
      fileInfo <- service.uploadFile(scheduleFile(), purpose = FileUploadPurpose.assistants)
    } yield {
      fileInfo
    }

}
