package io.cequence.openaiscala.examples.nonopenai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  ThinkingSettings
}
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageStreamedWithThinking extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.claude_3_7_sonnet_20250219

  override protected def run: Future[_] =
    service
      .createMessageStreamed(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = modelId,
          max_tokens = 10000,
          thinking = Some(ThinkingSettings(budget_tokens = 2000))
        )
      )
      .runWith(
        Sink.foreach { response =>
          print(response.text)
        }
      )
}
