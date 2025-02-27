package io.cequence.openaiscala.examples.anthropic

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageStreamed extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.claude_3_5_haiku_20241022

  override protected def run: Future[_] =
    service
      .createMessageStreamed(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = modelId,
          max_tokens = 4096
        )
      )
      .runWith(
        Sink.foreach { response =>
          print(response.text)
        }
      )
}
