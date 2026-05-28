package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Input,
  Response
}
import io.cequence.openaiscala.domain.responsesapi.tools.{Tool, ToolChoice}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

/**
 * Contract for adapting between OpenAI's Chat Completion shape and the Responses API.
 *
 * The default implementation lives on [[OpenAIResponsesChatCompletionService]], which mixes
 * this trait in. Downstream callers that need to layer extra behavior on top — e.g., a
 * fallback for tool-only responses with no synthesized text, or routing through an
 * MCP-tool-aware Responses request — can call into the default mapping methods and
 * post-process / pre-process, or supply alternative implementations.
 */
trait OpenAIResponsesChatCompletionMappingExt {

  /**
   * Convert a sequence of chat-completion [[BaseMessage]]s into the Responses-API form: a
   * flattened `instructions` string (collected from `System` / `Developer` messages) plus an
   * ordered list of [[Input]] items for the remaining turns. Handles user text / image / file
   * content, assistant tool calls and tool responses.
   */
  def convertMessages(messages: Seq[BaseMessage]): (Option[String], Seq[Input])

  /**
   * Translate [[CreateChatCompletionSettings]] into [[CreateModelResponseSettings]], threading
   * through the resolved instructions, tool list and tool-choice. Unsupported chat-completion
   * knobs are logged and dropped. The `tools` parameter accepts any Responses-API [[Tool]]
   * (function tools, MCP tools, etc.) so MCP-aware callers can reuse the same settings
   * mapping.
   */
  def toResponsesSettings(
    settings: CreateChatCompletionSettings,
    instructions: Option[String],
    tools: Seq[Tool],
    toolChoice: Option[ToolChoice]
  ): CreateModelResponseSettings

  /**
   * Map a Responses API [[Response]] to a [[ChatCompletionResponse]]. Function tool calls
   * present on the response are dropped on this path; callers that care about tool calls
   * should use the tool-aware chat-completion API.
   */
  def toOpenAIChatCompletionResponse(response: Response): ChatCompletionResponse
}
