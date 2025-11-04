package io.cequence.openaiscala.examples.anthropic.files

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicGetFileMetadata extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  // IMPORTANT: Replace this with an actual file ID from your workspace
  // You can get file IDs by running AnthropicListFiles
  val fileId = "file_01..."

  override protected def run: Future[_] = {
    println("=" * 60)
    println("Getting file metadata")
    println("=" * 60)
    println()

    service.getFileMetadata(fileId).map {
      case Some(metadata) =>
        println(s"File metadata retrieved successfully!")
        println(s"  File ID: ${metadata.id}")
        println(s"  Filename: ${metadata.filename}")
        println(s"  MIME type: ${metadata.mimeType}")
        println(s"  Size: ${metadata.sizeBytes} bytes")
        println(s"  Created: ${metadata.createdAt}")
        println(s"  Downloadable: ${metadata.downloadable}")
        println()
        println("=" * 60)
        println("Note: Use this to check if a file exists before downloading")
        println("=" * 60)

      case None =>
        println(s"File not found: $fileId")
        println()
        println("=" * 60)
        println("Tips:")
        println("  - The file may have been deleted")
        println("  - Check the file ID is correct")
        println("  - Use AnthropicListFiles to see available files")
        println("=" * 60)
    }
  }
}
