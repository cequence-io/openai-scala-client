package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{BufferedImageHelper, ExampleBase}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * Google Gemini (via OpenAI-compatible adapter) with a PDF passed as `FileContent`.
 *
 * Requires:
 *   - `GOOGLE_API_KEY`
 *   - `EXAMPLE_PDF_PATH`
 */
object GoogleGeminiCreateChatCompletionWithFileContentAndPdf
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localPath = sys.env("EXAMPLE_PDF_PATH")
  private val localFile = new File(localPath)

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

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
        settings = CreateChatCompletionSettings(model = NonOpenAIModelId.gemini_2_5_pro)
      )
      .map(printMessageContent)
}
