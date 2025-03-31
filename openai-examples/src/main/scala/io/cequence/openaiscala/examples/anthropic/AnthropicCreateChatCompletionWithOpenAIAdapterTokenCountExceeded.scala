package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionWithOpenAIAdapterTokenCountExceeded
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropic()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?" * 100000)
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_3_5_haiku_20241022)
      )
      .map { content =>
        println(content.contentHead)
      }
}
