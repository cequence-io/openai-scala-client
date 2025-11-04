package io.cequence.openaiscala.examples.anthropic.files

import akka.stream.scaladsl.FileIO
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import java.nio.file.Paths
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicDownloadFile extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  // IMPORTANT: Replace this with an actual file ID from your workspace
  // You can get file IDs by running AnthropicListFiles
  private val fileId = "file_01..."

  override protected def run: Future[_] = {
    println("=" * 60)
    println("Downloading file")
    println("=" * 60)
    println()

    // Expand home directory properly (~ doesn't work with Paths.get)
    val homeDir = System.getProperty("user.home")

    for {
      // First, get file metadata to retrieve the actual filename
      metadataOpt <- service.getFileMetadata(fileId)

      // Then download the file
      sourceOpt <- service.downloadFile(fileId)

      result <- (metadataOpt, sourceOpt) match {
        case (Some(metadata), Some(source)) =>
          val outputPath = Paths.get(homeDir, "Downloads", metadata.filename)

          println(s"Downloading file: $fileId")
          println(s"  Filename: ${metadata.filename}")
          println(s"  Size: ${metadata.sizeBytes} bytes")
          println(s"  MIME type: ${metadata.mimeType}")
          println(s"Saving to: $outputPath")
          println()

          // Stream the file contents to disk
          source
            .runWith(FileIO.toPath(outputPath))
            .map { ioResult =>
              println(s"Download complete!")
              println(s"  Bytes written: ${ioResult.count}")
              println(s"  Saved to: $outputPath")
              println()
              println("=" * 60)
              println("Note: Large files are streamed efficiently")
              println("=" * 60)
            }
            .recover { case ex =>
              println(s"Error downloading file: ${ex.getMessage}")
            }

        case (None, _) =>
          println(s"File not found: $fileId")
          println()
          println("=" * 60)
          println("Tips:")
          println("  - The file may have been deleted")
          println("  - Check the file ID is correct")
          println("  - Use AnthropicListFiles to see available files")
          println("=" * 60)
          Future.successful(())

        case (_, None) =>
          println(s"File content not available for: $fileId")
          println("Note: Only Claude-generated files are downloadable")
          Future.successful(())
      }
    } yield result
  }
}
