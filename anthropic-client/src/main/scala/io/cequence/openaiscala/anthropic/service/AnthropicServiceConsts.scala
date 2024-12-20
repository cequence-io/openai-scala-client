package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.domain.NonOpenAIModelId

/**
 * Constants of [[AnthropicService]], mostly defaults
 */
trait AnthropicServiceConsts {

  protected val defaultCoreUrl = "https://api.anthropic.com/v1/"

  protected def bedrockCoreUrl(region: String) =
    s"https://bedrock-runtime.$region.amazonaws.com/"

  object DefaultSettings {

    val CreateMessage = AnthropicCreateMessageSettings(
      model = NonOpenAIModelId.claude_2_1,
      max_tokens = 2048
    )
  }
}
