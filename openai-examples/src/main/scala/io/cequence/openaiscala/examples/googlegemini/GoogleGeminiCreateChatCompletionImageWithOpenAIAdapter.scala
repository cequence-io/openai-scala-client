package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future
import io.cequence.openaiscala.examples.BufferedImageHelper

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionImageWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

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

  private val modelId = NonOpenAIModelId.gemini_2_5_pro

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId
        )
      )
      .map(printMessageContent)
}
