package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// Requires `openai-scala-anthropic-client` as a dependency and env vars:
//   AWS_BEDROCK_ACCESS_KEY  - long-lived IAM user access key (AKIA...)
//   AWS_BEDROCK_SECRET_KEY  - long-lived IAM user secret
//   AWS_BEDROCK_REGION      - e.g. eu-central-1
//
// On startup, the factory calls STS:GetSessionToken to mint a short-lived ASIA-prefixed
// triple, then uses it for SigV4-signed Bedrock calls. No AWS SDK or CLI dependency.
object AnthropicBedrockCreateMessageWithStsSessionTokenAndOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    AnthropicServiceFactory.bedrockAsOpenAIWithSessionToken()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the capital of Norway? One word.")
  )

  private val modelId =
    "eu." + NonOpenAIModelId.bedrock_claude_sonnet_4_5_20250929_v1_0

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(modelId)
      )
      .map(response => println(response.contentHead))
}
