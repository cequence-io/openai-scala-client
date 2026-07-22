package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.domain.NonOpenAIModelId

/**
 * Constants of [[AnthropicService]], mostly defaults
 */
trait AnthropicServiceConsts {

  protected val defaultCoreUrl = "https://api.anthropic.com/v1/"

  protected def bedrockCoreUrl(region: String): String =
    s"https://bedrock-runtime.$region.amazonaws.com/"

  // Amazon Bedrock `bedrock-mantle` endpoint: Claude models are served from the
  // provider-scoped `anthropic/v1` base path as the Anthropic-native Messages API
  // (they reject the OpenAI-compatible `/v1/chat/completions` and `/v1/responses` paths)
  protected def bedrockMantleCoreUrl(region: String): String =
    s"https://bedrock-mantle.$region.api.aws/anthropic/v1/"

  object DefaultSettings {

    val CreateMessage = AnthropicCreateMessageSettings(
      model = NonOpenAIModelId.claude_haiku_4_5,
      max_tokens = 2048
    )
  }

  /**
   * The model's real max output (sync Messages API) - used as the `max_tokens` fallback when
   * the caller doesn't set one, instead of the flat, tiny `DefaultSettings.CreateMessage.
   * max_tokens` (2048), which silently truncates large completions (live-verified: an
   * 85-entity JSON extraction on claude-sonnet-4-6 was cut off mid-JSON at exactly 2048 tokens
   * with no error, because `max_tokens: null` fell back to that flat constant instead of the
   * model's real cap). Source:
   * https://platform.claude.com/docs/en/about-claude/models/overview (checked 2026-07-20) -
   * values are for the SYNCHRONOUS Messages API (the Batches API allows up to 300k on several
   * of these models via the `output-300k-2026-03-24` beta header, not modeled here). Short ids
   * match Bedrock/Vertex-prefixed model strings too (see `outputEffortModels` above for the
   * same `contains` convention). Models not covered here (older/legacy families we have no
   * freshly-verified numbers for) keep the flat 2048 fallback rather than a guessed value.
   */
  private val maxOutputTokensByModel: Seq[(String, Int)] = Seq(
    NonOpenAIModelId.claude_fable_5 -> 128000,
    NonOpenAIModelId.claude_opus_4_8 -> 128000,
    NonOpenAIModelId.claude_opus_4_7 -> 128000,
    NonOpenAIModelId.claude_opus_4_6 -> 128000,
    NonOpenAIModelId.claude_sonnet_5 -> 128000,
    NonOpenAIModelId.claude_sonnet_4_6 -> 128000,
    NonOpenAIModelId.claude_opus_4_5 -> 64000,
    NonOpenAIModelId.claude_sonnet_4_5 -> 64000,
    NonOpenAIModelId.claude_haiku_4_5 -> 64000,
    NonOpenAIModelId.claude_opus_4_1_20250805 -> 32000
  )

  protected def defaultMaxTokens(model: String): Int = {
    val m = model.toLowerCase
    maxOutputTokensByModel.collectFirst { case (id, maxTokens) if m.contains(id) => maxTokens }
      .getOrElse(DefaultSettings.CreateMessage.max_tokens)
  }
}
