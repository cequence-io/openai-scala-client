package io.cequence.openaiscala.domain.responsesapi.tools.mcp

import io.cequence.openaiscala.domain.responsesapi.Input

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
 * @param error
 *   The error from the tool call, if any.
 * @param output
 *   The output from the tool call, if any.
 */
final case class MCPToolCall(
  arguments: String,
  id: String,
  name: String,
  serverLabel: String,
  error: Option[String] = None,
  output: Option[String] = None
) extends Input {
  val `type`: String = "mcp_call"
}
