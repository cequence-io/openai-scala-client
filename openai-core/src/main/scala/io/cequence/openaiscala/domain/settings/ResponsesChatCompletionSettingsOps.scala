package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.responsesapi.tools.Tool

/**
 * Responses-API-specific knobs carried in `CreateChatCompletionSettings.extra_params` for the
 * chat-completion-shaped Responses adapter (`OpenAIResponsesChatCompletionService`,
 * `service.responsesAsChatCompletion`, `createChatToolCompletionStreamedViaResponses`).
 */
object ResponsesChatCompletionSettingsOps {

  val ResponsesToolsParam = "responses_tools"
  val ResponsesReasoningSummaryParam = "responses_reasoning_summary"

  /** The extra-params keys consumed by the Responses adapter (never sent to the API). */
  val knownParams: Set[String] = Set(ResponsesToolsParam, ResponsesReasoningSummaryParam)

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
  }
}
