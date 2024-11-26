package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._

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
          NonOpenAIModelId.claude_3_5_sonnet_20241022
        ).setUseAnthropicSystemMessagesCache(true), // this is how we pass it through the adapter
      )
      .map { content =>
        println(content.choices.headOption.map(_.message.content).getOrElse("N/A"))
      }
}
