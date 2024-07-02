package io.cequence.openaiscala.examples

import java.nio.file.Paths
import scala.concurrent.Future

object RetrieveFileContentStreamed extends Example {

  private val fileId = "file-xxx"

  override protected def run: Future[_] =
    for {
      fileInfoOption <- service.retrieveFile(fileId)

      sourceOption <- service.retrieveFileContentAsSource(fileId)

      _ <- (fileInfoOption, sourceOption).zipped.headOption.map { case (fileInfo, source) =>
        source.runWith(
          akka.stream.scaladsl.FileIO.toPath(Paths.get(fileInfo.filename))
        )
      }.getOrElse(
        Future.failed(new Exception("File not found"))
      )
    } yield ()
}
