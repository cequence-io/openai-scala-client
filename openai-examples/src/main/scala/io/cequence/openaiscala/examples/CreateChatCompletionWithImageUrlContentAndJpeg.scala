package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import java.io.File
import java.nio.file.Files
import java.util.Base64
import scala.concurrent.Future

/**
 * Pure OpenAI chat completion with a JPEG passed as `ImageURLContent` (data URL).
 *
 * This is the supported OpenAI path for images — `FileContent` on OpenAI is for PDFs only.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `EXAMPLE_PDF_PATH` (a JPEG works fine here too).
 */
object CreateChatCompletionWithImageUrlContentAndJpeg extends Example {

  private val localPath = sys.env("EXAMPLE_PDF_PATH")
  private val localFile = new File(localPath)
  private val base64 = Base64.getEncoder.encodeToString(Files.readAllBytes(localFile.toPath))
  private val dataUrl = s"data:image/jpeg;base64,$base64"

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant that summarizes documents."),
    UserSeqMessage(
      Seq(
        TextContent("Summarize this document in 3 bullet points."),
        ImageURLContent(dataUrl)
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model = ModelId.gpt_5_4_mini)
      )
      .map(printMessageContent)
}
