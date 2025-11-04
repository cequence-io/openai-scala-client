package io.cequence.openaiscala.examples.anthropic.files

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicListFiles extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  override protected def run: Future[_] = {
    println("=" * 60)
    println("Listing files in workspace")
    println("=" * 60)
    println()

    // List first 10 files
    service.listFiles(limit = Some(10)).map { response =>
      println(s"Files found: ${response.data.length}")
      println(s"Has more: ${response.hasMore}")
      response.firstId.foreach(id => println(s"First ID: $id"))
      response.lastId.foreach(id => println(s"Last ID: $id"))
      println()

      if (response.data.isEmpty) {
        println("No files in workspace")
        println()
        println("=" * 60)
        println("Note: Use createFile to upload files first")
        println("=" * 60)
      } else {
        response.data.foreach { file =>
          println(s"File ID: ${file.id}")
          println(s"  Filename: ${file.filename}")
          println(s"  MIME type: ${file.mimeType}")
          println(s"  Size: ${file.sizeBytes} bytes")
          println(s"  Created: ${file.createdAt}")
          println(s"  Downloadable: ${file.downloadable}")
          println()
        }

        println("=" * 60)
        println("Pagination:")
        println("  - Use beforeId parameter to get previous page")
        println("  - Use afterId parameter to get next page")
        println("  - Limit ranges from 1 to 1000 (default 20)")
        println("=" * 60)
      }
    }
  }
}
