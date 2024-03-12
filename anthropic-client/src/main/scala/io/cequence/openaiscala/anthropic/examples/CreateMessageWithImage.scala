package io.cequence.openaiscala.anthropic.examples

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{ImageBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.UserMessageContent
import io.cequence.openaiscala.anthropic.service.AnthropicCreateMessageSettings
import io.cequence.openaiscala.domain.ModelId

import java.awt.image.RenderedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import scala.concurrent.Future

object CreateMessageWithImage extends Example {

  private val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val bufferedImage = ImageIO.read(new java.io.File(localImagePath))
  private val imageBase64Source =
    Base64.getEncoder.encodeToString(imageToBytes(bufferedImage, "jpeg"))

  val messages: Seq[Message] = Seq(
    UserMessageContent(
      Seq(
        TextBlock("Describe me what is in the picture!"),
        ImageBlock(
          `type` = "base64",
          mediaType = "image/jpeg",
          data = imageBase64Source
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = ModelId.claude_3_opus_20240229,
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

}
