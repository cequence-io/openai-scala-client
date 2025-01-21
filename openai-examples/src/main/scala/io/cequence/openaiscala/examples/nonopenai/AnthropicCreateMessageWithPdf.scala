package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{MediaBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessageContent}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.{BufferedImageHelper, ExampleBase}

import java.io.File
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency
object AnthropicCreateMessageWithPdf
    extends ExampleBase[AnthropicService]
    with BufferedImageHelper {

  private val localPdfPath = sys.env("EXAMPLE_PDF_PATH")
  private val base64Source = pdfBase64Source(new File(localPdfPath))

  override protected val service: AnthropicService = AnthropicServiceFactory(withPdf = true)

  private val messages: Seq[Message] = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessageContent(
      Seq(
        ContentBlockBase(TextBlock("Summarize the document.")),
        MediaBlock.pdf(data = base64Source)
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model =
            NonOpenAIModelId.claude_3_5_sonnet_20241022, // claude-3-5-sonnet-20241022 supports PDF (beta)
          max_tokens = 8192
        )
      )
      .map(printMessageContent)

  private def printMessageContent(response: CreateMessageResponse) = {
    val text =
      response.content.blocks.collect { case ContentBlockBase(TextBlock(text), _) => text }
        .mkString(" ")
    println(text)
  }
}
