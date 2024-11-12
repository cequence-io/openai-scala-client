package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{MediaBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.UserMessageContent
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import java.awt.image.RenderedImage
import java.io.{ByteArrayOutputStream, File}
import java.nio.file.Files
import java.util.Base64
import javax.imageio.ImageIO
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency
object AnthropicCreateMessageWithPdf extends ExampleBase[AnthropicService] {

  private val localImagePath = sys.env("EXAMPLE_PDF_PATH")
  private val pdfBase64Source =
    Base64.getEncoder.encodeToString(readPdfToBytes(localImagePath))

  override protected val service: AnthropicService = AnthropicServiceFactory(withPdf = true)

  private val messages: Seq[Message] = Seq(
    UserMessageContent(
      Seq(
        ContentBlockBase(TextBlock("Describe to me what is this PDF about!")),
        MediaBlock.pdf(data = pdfBase64Source)
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        None,
        settings = AnthropicCreateMessageSettings(
          model =
            NonOpenAIModelId.claude_3_5_sonnet_20241022, // claude-3-5-sonnet-20241022 supports PDF (beta)
          max_tokens = 8192
        )
      )
      .map(printMessageContent)

  def readPdfToBytes(filePath: String): Array[Byte] = {
    val pdfFile = new File(filePath)
    Files.readAllBytes(pdfFile.toPath)
  }

  private def printMessageContent(response: CreateMessageResponse) = {
    val text =
      response.content.blocks.collect { case ContentBlockBase(TextBlock(text), _) => text }
        .mkString(" ")
    println(text)
  }
}
