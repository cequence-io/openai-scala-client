package io.cequence.openaiscala.domain.responsesapi.tools.mcp

import io.cequence.openaiscala.domain.responsesapi.{Input, ModelStatus, Output}
import io.cequence.openaiscala.domain.responsesapi.tools.ToolCall

/**
 * An invocation of a tool on an MCP server.
 *
 * @param arguments
 *   A JSON string of the arguments passed to the tool.
 * @param id
 *   The unique ID of the tool call.
 * @param name
 *   The name of the tool that was run.
 * @param serverLabel
 *   The label of the MCP server running the tool.
 * @param approvalRequestId
 *   Unique identifier for the MCP tool call approval request.
 * @param error
 *   The error from the tool call, if any.
 * @param output
 *   The output from the tool call, if any.
 * @param status
 *   The status of the tool call. One of in_progress, completed, incomplete, calling, or
 *   failed.
 */
final case class MCPToolCall(
  arguments: String,
  id: String,
  name: String,
  serverLabel: String,
  approvalRequestId: Option[String] = None,
  error: Option[MCPToolError] = None,
  output: Option[String] = None,
  status: Option[ModelStatus] = None
) extends ToolCall
    with Input
    with Output {
  val `type`: String = "mcp_call"
}
