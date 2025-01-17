package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.{BufferedImageHelper, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.io.File
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionWithOpenAIAdapterAndPdf
    extends ExampleBase[OpenAIChatCompletionService]
      with BufferedImageHelper {

  private val localPdfPath = sys.env("EXAMPLE_PDF_PATH")
  private val base64Source = pdfBase64Source(new File(localPdfPath))

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropic()

  private val messages = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserSeqMessage(
      Seq(
        TextContent("Summarize the document."),
        ImageURLContent(s"data:application/pdf;base64,${base64Source}")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_3_5_sonnet_20241022)
      )
      .map { content =>
        println(content.choices.headOption.map(_.message.content).getOrElse("N/A"))
      }
}
