package io.cequence.openaiscala.domain.responsesapi.tools.mcp

/**
 * A tool available on an MCP server.
 *
 * @param inputSchema
 *   The JSON schema describing the tool's input.
 * @param name
 *   The name of the tool.
 * @param annotations
 *   Additional annotations about the tool.
 * @param description
 *   The description of the tool.
 */
final case class MCPToolRef(
  inputSchema: Map[String, Any],
  name: String,
  annotations: Map[String, Any] = Map(),
  description: Option[String] = None
) {
  val `type`: String = "mcp_tool_ref"
}
