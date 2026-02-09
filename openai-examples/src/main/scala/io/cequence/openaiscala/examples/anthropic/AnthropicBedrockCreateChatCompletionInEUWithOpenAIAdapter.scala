package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and 'AWS_BEDROCK_ACCESS_KEY', 'AWS_BEDROCK_SECRET_KEY', 'AWS_BEDROCK_REGION' environment variable to be set
object AnthropicBedrockCreateChatCompletionInEUWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropicBedrock

  private val messages = Seq(
    SystemMessage("You are a drunk assistant!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId =
    "eu." + NonOpenAIModelId.bedrock_claude_sonnet_4_5_20250929_v1_0

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          modelId,
          reasoning_effort = Some(ReasoningEffort.high),
          max_tokens = Some(20000)
        ),
      )
      .map { content =>
        println(content.contentHead)
      }
}
