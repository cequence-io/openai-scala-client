package io.cequence.openaiscala.examples.anthropic.files

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import java.io.File
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` and `EXAMPLE_FILE_PATH` environment variables to be set
object AnthropicCreateFile extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  override protected def run: Future[_] = {
    println("=" * 60)
    println("Creating (uploading) a file")
    println("=" * 60)
    println()

    // Read file path from environment variable
    val filePathOpt = sys.env.get("EXAMPLE_FILE_PATH")

    filePathOpt match {
      case None =>
        println("Error: EXAMPLE_FILE_PATH environment variable not set")
        println("Please set it to the path of the file you want to upload")
        println("Example: export EXAMPLE_FILE_PATH=/path/to/document.pdf")
        Future.successful(())

      case Some(filePath) =>
        val file = new File(filePath)

        if (!file.exists()) {
          println(s"Error: File does not exist: $filePath")
          println("Please check the EXAMPLE_FILE_PATH environment variable")
          Future.successful(())
        } else {
          // Upload file with optional custom filename
          service.createFile(file, None).map { fileMetadata =>
            println(s"File uploaded successfully!")
            println(s"  File ID: ${fileMetadata.id}")
            println(s"  Filename: ${fileMetadata.filename}")
            println(s"  MIME type: ${fileMetadata.mimeType}")
            println(s"  Size: ${fileMetadata.sizeBytes} bytes")
            println(s"  Created: ${fileMetadata.createdAt}")
            println(s"  Downloadable: ${fileMetadata.downloadable}")
            println()
            println("=" * 60)
            println("Note: Save the File ID to use in other operations")
            println("Note: Set EXAMPLE_FILE_NAME to use a custom filename")
            println("Note: User-uploaded files are always non-downloadable")
            println("=" * 60)
          }
        }
    }
  }
}
