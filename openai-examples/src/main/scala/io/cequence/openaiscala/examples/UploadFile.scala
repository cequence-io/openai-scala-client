package io.cequence.openaiscala.examples

import java.io.File
import java.nio.file.Paths
import scala.concurrent.Future

object UploadFile extends Example {

  private def scheduleFile(): File =
    Paths.get("~/proj/cequence/eBF programme 2024 - extracted.pdf").toFile

  override protected def run: Future[_] =
    for {
      fileInfo <- service.uploadFile(scheduleFile())
    } yield {
      fileInfo
    }

}
