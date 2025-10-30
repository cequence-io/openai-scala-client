package io.cequence.openaiscala.domain.responsesapi.tools.mcp

import io.cequence.openaiscala.domain.responsesapi.Input

/**
 * A list of tools available on an MCP server.
 *
 * @param id
 *   The unique ID of the list.
 * @param serverLabel
 *   The label of the MCP server.
 * @param tools
 *   The tools available on the server.
 * @param error
 *   Error message if the server could not list tools.
 */
final case class MCPListTools(
  id: String,
  serverLabel: String,
  tools: Seq[MCPToolRef],
  error: Option[String] = None
) extends Input {
  val `type`: String = "mcp_list_tools"
}
