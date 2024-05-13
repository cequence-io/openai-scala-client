package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Pagination
import io.cequence.openaiscala.domain.response.FileInfo

import java.io.{File, PrintWriter}
import scala.concurrent.Future

object UploadBatchFile extends Example {

  private def chatCompletionsTempFile(): File = {
    val tempFile = File.createTempFile("chat_completions", ".jsonl")
    val writer = new PrintWriter(tempFile)
    writer.write(
      """|{"custom_id": "request-1","method": "POST","url": "/v1/chat/completions","body": {  "model": "gpt-4", "messages": [{    "role": "system",    "content": "You are a helpful assistant."  },  {    "role": "user",    "content": "What is 2+2?"  }]}}""".stripMargin
    )
    writer.close()
    tempFile
  }

  override protected def run: Future[_] = {
    val file = chatCompletionsTempFile()
    for {
      fileInfo <- service.uploadBatchFile(file)
    } yield {
      fileInfo
    }
  }

}
