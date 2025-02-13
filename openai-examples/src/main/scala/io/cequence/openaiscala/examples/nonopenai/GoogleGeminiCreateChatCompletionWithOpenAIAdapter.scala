package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are a pirate who likes to joke."),
    UserMessage("Explain AI to a 5-year-old.")
  )

  private val modelId = NonOpenAIModelId.gemini_1_5_flash_001

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
