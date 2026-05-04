package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// Requires `openai-scala-anthropic-client` as a dependency and env vars:
//   AWS_BEARER_TOKEN_BEDROCK  - the Bedrock API key
//   AWS_BEDROCK_REGION        - e.g. us-east-1
//
// Bearer-token auth bypasses SigV4 entirely; no accessKey/secretKey needed.
object AnthropicBedrockCreateMessageWithBearerTokenAndOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the capital of Norway? One word.")
  )

  private val modelId =
    // 'us.' prefix for cross-region inference
    "us." + NonOpenAIModelId.bedrock_claude_sonnet_4_20250514_v1_0

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(modelId)
      )
      .map(response => println(response.contentHead))
}
