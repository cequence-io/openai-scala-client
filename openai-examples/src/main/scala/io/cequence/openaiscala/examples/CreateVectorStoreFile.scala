package io.cequence.openaiscala.examples

import io.cequence.openaiscala.examples.UploadBatchFile.service

import java.io.{File, PrintWriter}
import scala.concurrent.Future

object CreateVectorStoreFile extends Example {

  private def knowledgeTempFile(): File = {
    val tempFile = File.createTempFile("ice-hockey", ".txt")
    val writer = new PrintWriter(tempFile)
    writer.write(
      """It's 2024 and Czech Republic became the World Champion in ice-hockey.
        |The team won the final game against Switzerland 2:0.
        |""".stripMargin
    )
    writer.close()
    tempFile
  }

  override protected def run: Future[_] = {
    val file = knowledgeTempFile()
    for {
      fileInfo <- service.uploadFile(file)
      vectorStore <- service.createVectorStore(
        fileIds = Seq.empty,
        name = Some("Ice-hockey fans")
      )
      vectorStoreFile <- service.createVectorStoreFile(
        vectorStoreId = vectorStore.id,
        fileId = fileInfo.id
      )
    } yield {
      println(vectorStoreFile)
    }
  }

}
