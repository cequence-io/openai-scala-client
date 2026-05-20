package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{
  BufferedImageHelper,
  ChatCompletionProvider,
  ExampleBase
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * Anthropic (via OpenAI-compatible adapter) with a PDF passed as `FileContent`.
 *
 * Requires:
 *   - `ANTHROPIC_API_KEY`
 *   - `EXAMPLE_PDF_PATH`
 */
object AnthropicCreateChatCompletionWithFileContentAndPdf
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localPath = sys.env("EXAMPLE_PDF_PATH")
  private val localFile = new File(localPath)

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropic()

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
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_4_5_20250929
        )
      )
      .map(response => println(response.contentHead))
}
