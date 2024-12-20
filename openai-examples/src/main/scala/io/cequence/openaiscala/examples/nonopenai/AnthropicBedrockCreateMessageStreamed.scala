package io.cequence.openaiscala.examples.nonopenai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and 'AWS_BEDROCK_ACCESS_KEY', 'AWS_BEDROCK_SECRET_KEY', 'AWS_BEDROCK_REGION' environment variable to be set
object AnthropicBedrockCreateMessageStreamed extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory.forBedrock()

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant!"),
    UserMessage(
      "Start with the letter S followed by a quick story about Norway and finish with the letter E."
    )
  )

  private val modelId = "us." + NonOpenAIModelId.bedrock_claude_3_5_sonnet_20241022_v2_0

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
          print(response.delta.text)
        }
      )
}
