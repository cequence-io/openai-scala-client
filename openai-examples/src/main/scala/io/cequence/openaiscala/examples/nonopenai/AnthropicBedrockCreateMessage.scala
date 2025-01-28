package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and 'AWS_BEDROCK_ACCESS_KEY', 'AWS_BEDROCK_SECRET_KEY', 'AWS_BEDROCK_REGION' environment variable to be set
object AnthropicBedrockCreateMessage extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory.forBedrock()

  private val messages: Seq[Message] = Seq(
    SystemMessage("You are a drunk assistant!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId =
    // using 'us.' prefix because of the cross-region inference (enabled only in the us)
    "us." + NonOpenAIModelId.bedrock_claude_3_5_sonnet_20241022_v2_0

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = modelId,
          max_tokens = 4096,
          temperature = Some(1.0)
        )
      )
      .map(printMessageContent)

  private def printMessageContent(response: CreateMessageResponse) =
    println(response.text)
}
