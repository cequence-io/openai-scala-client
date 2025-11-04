package io.cequence.openaiscala.examples.anthropic.files

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicDeleteFile extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  override protected def run: Future[_] = {
    println("=" * 60)
    println("Deleting file")
    println("=" * 60)
    println()

    // IMPORTANT: Replace this with an actual file ID from your workspace
    // You can get file IDs by running AnthropicListFiles
    val fileId = "file_01..."

    println(s"Attempting to delete file: $fileId")
    println()

    service
      .deleteFile(fileId)
      .map { response =>
        println(s"File deleted successfully!")
        println(s"  Deleted file ID: ${response.id}")
        println(s"  Type: ${response.`type`}")
        println()
        println("=" * 60)
        println("Note: Deleted files cannot be recovered")
        println("Note: The file is now inaccessible through the API")
        println("=" * 60)
      }
      .recover {
        case ex if ex.getMessage.contains("not_found") =>
          println(s"File not found: $fileId")
          println("The file may have already been deleted")
          println()
          println("=" * 60)
          println("Tips:")
          println("  - Use AnthropicListFiles to see available files")
          println("  - Use AnthropicGetFileMetadata to check if file exists")
          println("=" * 60)

        case ex =>
          println(s"Error deleting file: ${ex.getMessage}")
          println()
          println("=" * 60)
          println("Note: Check the error message for details")
          println("=" * 60)
      }
  }
}
