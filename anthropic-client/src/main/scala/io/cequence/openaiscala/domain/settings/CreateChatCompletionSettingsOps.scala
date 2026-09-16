package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.anthropic.domain.tools.{MCPServerURLDefinition, Tool}

import scala.util.Try

object CreateChatCompletionSettingsOps {
  implicit class RichCreateChatCompletionSettings(settings: CreateChatCompletionSettings) {
    private val AnthropicCachedUserMessagesCount = "cached_user_messages_count"
    private val AnthropicUseSystemMessagesCache = "use_system_messages_cache"
    private val AnthropicThinkingBudgetTokens = "thinking_budget_tokens"
    private val AnthropicFastSpeed = "fast_speed"
    private val AnthropicTools = "anthropic_tools"
    private val AnthropicMcpServers = "anthropic_mcp_servers"
    private val AnthropicMaxContinuations = "anthropic_max_continuations"

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

    /**
     * Remote MCP servers for Anthropic's MCP connector (`mcp_servers` on the Messages API,
     * Anthropic API only - Bedrock rejects it). Claude calls their tools itself; on the typed
     * stream each call arrives as `ToolCallStart` / `ToolCall` (`serverSide = true`) and its
     * result as `ToolResult`. When a server-side tool run exceeds the turn budget the API
     * stops with `pause_turn`; the adapter continues the turn transparently, see
     * [[setAnthropicMaxContinuations]].
     */
    def setAnthropicMcpServers(
      servers: Seq[MCPServerURLDefinition]
    ): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicMcpServers -> servers)
      )

    def anthropicMcpServers: Seq[MCPServerURLDefinition] =
      settings.extra_params.get(AnthropicMcpServers) match {
        case Some(servers: Seq[_]) if servers.forall(_.isInstanceOf[MCPServerURLDefinition]) =>
          servers.asInstanceOf[Seq[MCPServerURLDefinition]]
        case _ => Nil
      }

    /**
     * How many times the Anthropic adapter re-issues a request that stopped with `pause_turn`
     * (a server-side / MCP tool run that outlived its turn budget) before giving up and
     * reporting that stop reason. Defaults to 6; 0 disables the continuation.
     */
    def setAnthropicMaxContinuations(max: Int): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (AnthropicMaxContinuations -> max)
      )

    def anthropicMaxContinuations: Option[Int] =
      settings.extra_params.get(AnthropicMaxContinuations).flatMap {
        case value: Int => Some(value)
        case value: Any => Try(value.toString.toInt).toOption
      }
  }
}
