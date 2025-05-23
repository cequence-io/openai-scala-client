package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionCachedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    ChatCompletionProvider.anthropic(withCache = true)

  private val messages = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          NonOpenAIModelId.claude_3_7_sonnet_20250219
            // this is how we pass it through the adapter
        ).setUseAnthropicSystemMessagesCache(true)
      )
      .map { content =>
        println(content.contentHead)
      }
}
