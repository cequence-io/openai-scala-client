package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `DEEPSEEK_API_KEY` environment variable to be set.
 */
object DeepseekCreateChatCompletion extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.deepseek

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.deepseek_chat

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(1024)
        )
      )
      .map(printMessageContent)
}
