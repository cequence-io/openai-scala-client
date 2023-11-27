package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import java.awt.image.RenderedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import scala.concurrent.Future

object GPTVisionWithLocalFile extends Example {

  // provide a local jpeg here
  private val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val bufferedImage = ImageIO.read(new java.io.File(localImagePath))
  private val imageBase64Source =
    Base64.getEncoder.encodeToString(imageToBytes(bufferedImage, "jpeg"))

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent("What is in this picture?"),
        ImageURLContent(s"data:image/jpeg;base64,${imageBase64Source}")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_4_vision_preview,
          temperature = Some(0),
          max_tokens = Some(300)
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
