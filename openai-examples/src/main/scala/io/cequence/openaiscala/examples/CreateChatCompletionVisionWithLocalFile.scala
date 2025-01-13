package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

object CreateChatCompletionVisionWithLocalFile extends Example with BufferedImageHelper {

  // provide a local jpeg here
  private lazy val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val imageSource = imageBase64Source(new java.io.File(localImagePath))

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent("What is in this picture?"),
        ImageURLContent(s"data:image/jpeg;base64,${imageSource}")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_4o,
          temperature = Some(0),
          max_tokens = Some(300)
        )
      )
      .map(printMessageContent)
}
