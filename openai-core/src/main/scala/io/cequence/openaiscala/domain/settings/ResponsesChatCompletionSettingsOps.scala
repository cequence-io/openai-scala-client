package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.responsesapi.{MultiAgentConfig, ReasoningMode}
import io.cequence.openaiscala.domain.responsesapi.tools.Tool

/**
 * Responses-API-specific knobs carried in `CreateChatCompletionSettings.extra_params` for the
 * chat-completion-shaped Responses adapter (`OpenAIResponsesChatCompletionService`,
 * `service.responsesAsChatCompletion`, `createChatToolCompletionStreamedViaResponses`).
 */
object ResponsesChatCompletionSettingsOps {

  val ResponsesToolsParam = "responses_tools"
  val ResponsesReasoningSummaryParam = "responses_reasoning_summary"
  val ResponsesReasoningModeParam = "responses_reasoning_mode"
  val ResponsesMultiAgentParam = "responses_multi_agent"

  /** The extra-params keys consumed by the Responses adapter (never sent to the API). */
  val knownParams: Set[String] = Set(
    ResponsesToolsParam,
    ResponsesReasoningSummaryParam,
    ResponsesReasoningModeParam,
    ResponsesMultiAgentParam
  )

  implicit class RichResponsesCreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {

    /**
     * Responses-API-native tools (e.g. `WebSearchTool()`, `CodeInterpreterTool(...)`,
     * `FileSearchTool(...)`, `MCPTool(...)`) to send in addition to the OpenAI function tools
     * of `createChatToolCompletion(Streamed)`. Their server-side activity comes back as
     * `ChatChunk.ToolCall(serverSide = true)` / `ToolResult` plus the semantic-layer chunks
     * (`WebSearch`, `CodeExecution`, `Image`, ...) on the typed stream.
     */
    def setResponsesTools(tools: Seq[Tool]): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (ResponsesToolsParam -> tools)
      )

    def responsesTools: Seq[Tool] =
      settings.extra_params.get(ResponsesToolsParam) match {
        case Some(tools: Seq[_]) if tools.forall(_.isInstanceOf[Tool]) =>
          tools.asInstanceOf[Seq[Tool]]
        case _ => Nil
      }

    /**
     * Whether reasoning summaries (`reasoning.summary = "auto"`) are requested when
     * `reasoning_effort` is set - the typed stream defaults to `true` so `ChatChunk.Thinking`
     * chunks arrive; pass `false` to skip them.
     */
    def setResponsesReasoningSummary(flag: Boolean = true): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (ResponsesReasoningSummaryParam -> flag)
      )

    def responsesReasoningSummary: Option[Boolean] =
      settings.extra_params.get(ResponsesReasoningSummaryParam).map(_.toString == "true")

    /**
     * `reasoning.mode` of the Responses API - `ReasoningMode.pro` asks the GPT-6 models for
     * more model work on difficult tasks (higher latency and token usage). The chat
     * completions API has no such parameter, so the full `OpenAIService` routes a call
     * carrying it through the Responses API, and anything that cannot refuses it.
     */
    def setResponsesReasoningMode(mode: ReasoningMode): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (ResponsesReasoningModeParam -> mode)
      )

    def responsesReasoningMode: Option[ReasoningMode] =
      settings.extra_params.get(ResponsesReasoningModeParam).collect {
        case mode: ReasoningMode => mode
      }

    /**
     * Server-hosted multi-agent execution of the Responses API (beta, GPT-6.1 Sol): the model
     * may delegate to subagents it spawns, messages and waits for, all on the server. Only the
     * root agent's answer becomes the completion's text; the delegation shows up on the typed
     * stream as server-side `multi_agent.<action>` tool calls / results and the subagents'
     * messages as `Other("subagent.message", ...)`. The chat completions API has no such
     * parameter, so the full `OpenAIService` routes a call carrying it through the Responses
     * API, and anything that cannot refuses it. Reasoning summaries are not requested with it
     * (the API rejects the combination).
     */
    def setResponsesMultiAgent(
      config: MultiAgentConfig = MultiAgentConfig()
    ): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (ResponsesMultiAgentParam -> config)
      )

    def responsesMultiAgent: Option[MultiAgentConfig] =
      settings.extra_params.get(ResponsesMultiAgentParam).collect {
        case config: MultiAgentConfig => config
      }
  }

  /**
   * The exception for a call carrying Responses-only settings - Responses-native tools
   * (`setResponsesTools`), a reasoning mode (`setResponsesReasoningMode`) or multi-agent
   * execution (`setResponsesMultiAgent`) - where they cannot be sent (None when there are
   * none): they are refused, never dropped.
   */
  def unsupportedResponsesSettings(
    settings: CreateChatCompletionSettings,
    entryPoint: String
  ): Option[OpenAIScalaClientException] = {
    val responsesOnly = Seq(
      if (settings.responsesTools.nonEmpty) Some("Responses-native tools (setResponsesTools)")
      else None,
      settings.responsesReasoningMode.map(mode =>
        s"reasoning mode '$mode' (setResponsesReasoningMode)"
      ),
      settings.responsesMultiAgent.map(_ => "multi-agent execution (setResponsesMultiAgent)")
    ).flatten

    if (responsesOnly.isEmpty) None
    else
      Some(
        new OpenAIScalaClientException(
          s"$entryPoint cannot send ${responsesOnly.mkString(" or ")} - they need the " +
            "Responses API: use createChatCompletion / createChatToolCompletion / the typed " +
            "createChatToolCompletionStreamed of the full OpenAIService, which route them there."
        )
      )
  }
}
