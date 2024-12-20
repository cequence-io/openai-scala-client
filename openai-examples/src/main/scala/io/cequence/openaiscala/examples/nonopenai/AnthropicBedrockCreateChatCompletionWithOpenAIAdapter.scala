package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and 'AWS_BEDROCK_ACCESS_KEY', 'AWS_BEDROCK_SECRET_KEY', 'AWS_BEDROCK_REGION' environment variable to be set
object AnthropicBedrockCreateChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropicBedrock

  private val messages = Seq(
    SystemMessage("You are a drunk assistant!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId =
    // using 'us.' prefix because of the cross-region inference (enabled only in the us)
    "us." + NonOpenAIModelId.bedrock_claude_3_5_haiku_20241022_v1_0

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(modelId)
      )
      .map { content =>
        println(content.choices.headOption.map(_.message.content).getOrElse("N/A"))
      }
}
