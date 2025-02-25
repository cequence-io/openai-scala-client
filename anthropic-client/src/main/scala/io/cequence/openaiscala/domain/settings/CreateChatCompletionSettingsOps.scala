package io.cequence.openaiscala.domain.settings

import scala.util.Try

object CreateChatCompletionSettingsOps {
  implicit class RichCreateChatCompletionSettings(settings: CreateChatCompletionSettings) {
    private val AnthropicCachedUserMessagesCount = "cached_user_messages_count"
    private val AnthropicUseSystemMessagesCache = "use_system_messages_cache"
    private val AnthropicThinkingBudgetTokens = "thinking_budget_tokens"

    def setAnthropicCachedUserMessagesCount(count: Int): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicCachedUserMessagesCount -> count)
      )

    def setUseAnthropicSystemMessagesCache(useCache: Boolean): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicUseSystemMessagesCache -> useCache)
      )

    def setAnthropicThinkingBudgetTokens(tokens: Int): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicThinkingBudgetTokens -> tokens)
      )

    def anthropicCachedUserMessagesCount: Int =
      settings.extra_params
        .get(AnthropicCachedUserMessagesCount)
        .flatMap {
          case value: Int => Some(value)
          case value: Any => Try(value.toString.toInt).toOption
        }
        .getOrElse(0)

    def useAnthropicSystemMessagesCache: Boolean =
      settings.extra_params
        .get(AnthropicUseSystemMessagesCache)
        .map(_.toString)
        .contains("true")

    def anthropicThinkingBudgetTokens: Option[Int] =
      settings.extra_params
        .get(AnthropicThinkingBudgetTokens)
        .flatMap {
          case value: Int => Some(value)
          case value: Any => Try(value.toString.toInt).toOption
        }
  }
}
