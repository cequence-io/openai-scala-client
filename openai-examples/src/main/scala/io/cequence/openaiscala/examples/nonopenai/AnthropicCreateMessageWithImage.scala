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
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency
object AnthropicCreateMessageWithImage extends ExampleBase[AnthropicService] {

  private val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val bufferedImage = ImageIO.read(new java.io.File(localImagePath))
  private val imageBase64Source =
    Base64.getEncoder.encodeToString(imageToBytes(bufferedImage, "jpeg"))

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val messages: Seq[Message] = Seq(
    UserMessageContent(
      Seq(
        ContentBlockBase(TextBlock("Describe to me what is in the picture!")),
        MediaBlock.jpeg(data = imageBase64Source)
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        system = None,
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_3_opus_20240229,
          max_tokens = 4096
        )
      )
      .map(printMessageContent)

  private def imageToBytes(
    image: RenderedImage,
    format: String
  ): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    ImageIO.write(image, format, baos)
    baos.flush()
    val imageInByte = baos.toByteArray
    baos.close()
    imageInByte
  }

  private def printMessageContent(response: CreateMessageResponse) = {
    val text =
      response.content.blocks.collect { case ContentBlockBase(TextBlock(text), _) => text }
        .mkString(" ")
    println(text)
  }
}
