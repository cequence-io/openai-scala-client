package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import java.io.File
import scala.concurrent.Future

/**
 * Pure OpenAI chat completion with a PDF passed as `FileContent`.
 *
 * Requires:
 *   - `OPENAI_SCALA_CLIENT_API_KEY`
 *   - `EXAMPLE_PDF_PATH` pointing to a local PDF file
 */
object CreateChatCompletionWithFileContentAndPdf extends Example with BufferedImageHelper {

  private val localPath = sys.env("EXAMPLE_PDF_PATH")
  private val localFile = new File(localPath)

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant that summarizes documents."),
    UserSeqMessage(
      Seq(
        TextContent("Summarize this document in 3 bullet points."),
        FileContent(
          fileData = Some(fileBase64DataUrl(localFile)),
          filename = Some(localFile.getName)
        )
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
