package io.cequence.openaiscala.domain.responsesapi.tools.mcp

import io.cequence.openaiscala.domain.responsesapi.Input

/**
 * A request for human approval of a tool invocation on an MCP server.
 *
 * @param arguments
 *   A JSON string of arguments for the tool.
 * @param id
 *   The unique ID of the approval request.
 * @param name
 *   The name of the tool to run.
 * @param serverLabel
 *   The label of the MCP server making the request.
 */
final case class MCPApprovalRequest(
  arguments: String,
  id: String,
  name: String,
  serverLabel: String
) extends Input {
  val `type`: String = "mcp_approval_request"
}
