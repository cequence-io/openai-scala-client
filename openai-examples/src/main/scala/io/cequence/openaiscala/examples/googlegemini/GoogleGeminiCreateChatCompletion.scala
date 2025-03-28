package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
// TODO: remove this... we have a better provider for Gemini. See [[GoogleGeminiCreateChatCompletionJSONWithOpenAIAdapter]]
object GoogleGeminiCreateChatCompletion extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.gemini

  private val messages = Seq(
    UserMessage("Explain AI to a 5-year-old.")
  )

  private val modelId = NonOpenAIModelId.gemini_2_0_flash

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
