package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.responsesapi.tools._

/**
 * Output types hierarchy:
 *
 *   - Output message (object) - [[Message.OutputContent]]
 *   - File search tool call (object) - [[FileSearchToolCall]]
 *   - Function tool call (object) - [[FunctionToolCall]]
 *   - Web search tool call (object) - [[WebSearchToolCall]]
 *   - Computer tool call (object) - [[ComputerToolCall]]
 *   - Reasoning (object) - [[Reasoning]]
 *   - Image generation call (object) - [[ImageGenerationToolCall]]
 *   - Code interpreter tool call (object) - [[CodeInterpreterToolCall]]
 *   - Local shell call (object) - [[LocalShellToolCall]]
 *   - MCP tool call (object) - [[mcp.MCPToolCall]]
 *   - MCP list tools (object) - [[mcp.MCPListTools]]
 *   - MCP approval request (object) - [[mcp.MCPApprovalRequest]]
 *   - Custom tool call (object) - [[CustomToolCall]]
 */
trait Output {
  val `type`: String
}

object Output {

  // add shortcuts for its subclasses
  def ofOutputMessage(
    content: Seq[OutputMessageContent],
    id: String,
    status: ModelStatus
  ) = Message.OutputContent(content, id, status)

  def ofFileSearchToolCall(
    id: String,
    queries: Seq[String] = Nil,
    status: ModelStatus,
    results: Seq[FileSearchResult] = Nil
  ) = FileSearchToolCall(id, queries, status, results)

  def ofFunctionToolCall(
    arguments: String,
    callId: String,
    name: String,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = FunctionToolCall(arguments, callId, name, id, status)

  def ofWebSearchToolCall(
    action: WebSearchAction,
    id: String,
    status: ModelStatus
  ) = WebSearchToolCall(action, id, status)

  def ofComputerToolCall(
    action: ComputerToolAction,
    callId: String,
    id: String,
    pendingSafetyChecks: Seq[PendingSafetyCheck] = Nil,
    status: ModelStatus
  ) = ComputerToolCall(action, callId, id, pendingSafetyChecks, status)

  def ofReasoning(
    id: String,
    summary: Seq[SummaryText],
    status: Option[ModelStatus] = None
  ) = Reasoning(id, summary, Nil, None, status)
}
