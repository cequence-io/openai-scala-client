package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

/**
 * Calls Claude on Amazon Bedrock via the `bedrock-mantle` endpoint, which serves Claude models
 * from the Anthropic-native Messages API at the `anthropic/v1` base path (the models reject
 * mantle's OpenAI-compatible `/v1/chat/completions` and `/v1/responses` paths).
 *
 * Uses the short-form model ids advertised by mantle's `/v1/models` (e.g.
 * `anthropic.claude-haiku-4-5`) - NOT the dated `bedrock-runtime` ids - and a Bedrock API key
 * as a bearer token instead of SigV4 signing. Claude availability on mantle is region-gated
 * (July 2026: present in `eu-north-1`, absent in `us-east-2`).
 *
 * Requires `openai-scala-anthropic-client` as a dependency and env vars:
 *   - `AWS_BEARER_TOKEN_BEDROCK` - the Bedrock API key
 *   - `AWS_BEDROCK_REGION` - e.g. "eu-north-1"
 */
object AnthropicBedrockMantleCreateMessage extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory.forBedrockMantle()

  val messages: Seq[Message] = Seq(
    UserMessage("Can you explain the features of Amazon Bedrock? Be concise.")
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.bedrock_claude_haiku_4_5,
          max_tokens = 2048
        )
      )
      .map(printMessageContent)

  private def printMessageContent(response: CreateMessageResponse) =
    println(response.text)
}
