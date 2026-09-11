package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.anthropic.domain.tools.Tool

import scala.util.Try

object CreateChatCompletionSettingsOps {
  implicit class RichCreateChatCompletionSettings(settings: CreateChatCompletionSettings) {
    private val AnthropicCachedUserMessagesCount = "cached_user_messages_count"
    private val AnthropicUseSystemMessagesCache = "use_system_messages_cache"
    private val AnthropicThinkingBudgetTokens = "thinking_budget_tokens"
    private val AnthropicFastSpeed = "fast_speed"
    private val AnthropicTools = "anthropic_tools"

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
      settings.extra_params.get(AnthropicThinkingBudgetTokens).flatMap {
        case value: Int => Some(value)
        case value: Any => Try(value.toString.toInt).toOption
      }

    def setAnthropicFastSpeed(fast: Boolean = true): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicFastSpeed -> fast)
      )

    def anthropicFastSpeed: Boolean =
      settings.extra_params.get(AnthropicFastSpeed).map(_.toString).contains("true")

    /**
     * Anthropic-native tools (e.g. `Tool.webSearch()`, `Tool.codeExecution()`) to send in
     * addition to the OpenAI function tools of `createChatToolCompletion(Streamed)`. Their
     * server-side results come back as `ChatChunk.ToolResult`s on the typed stream.
     */
    def setAnthropicTools(tools: Seq[Tool]): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicTools -> tools)
      )

    def anthropicTools: Seq[Tool] =
      settings.extra_params.get(AnthropicTools) match {
        case Some(tools: Seq[_]) if tools.forall(_.isInstanceOf[Tool]) =>
          tools.asInstanceOf[Seq[Tool]]
        case _ => Nil
      }
  }
}
