package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{BufferedImageHelper, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `GROK_API_KEY` environment variable to be set.
 */
object GrokCreateChatCompletionWithImage
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.grok

  private val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val imageSource = imageBase64Source(new java.io.File(localImagePath))

  private val messages = Seq(
    SystemMessage("You are a helpful document processing / OCR expert."),
    UserSeqMessage(
      Seq(
        TextContent("Please extract the hand written part from this image/report."),
        ImageURLContent(s"data:image/jpeg;base64,${imageSource}")
      )
    )
  )

  private val modelId = NonOpenAIModelId.grok_2_vision_latest

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0),
          max_tokens = Some(5000)
        )
      )
      .map(printMessageContent)
}
