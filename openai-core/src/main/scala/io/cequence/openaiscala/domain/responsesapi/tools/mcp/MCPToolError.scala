package io.cequence.openaiscala.domain.responsesapi.tools.mcp

/**
 * Content item within an MCP tool execution error.
 *
 * @param text
 *   The error message text.
 * @param annotations
 *   Optional annotations associated with the error content.
 * @param meta
 *   Optional metadata associated with the error content.
 */
final case class MCPToolErrorContent(
  text: String,
  annotations: Option[Map[String, Any]] = None,
  meta: Option[Map[String, Any]] = None
) {
  val `type`: String = "text"
}

/**
 * Error information from an MCP tool call execution.
 *
 * @param `type`
 *   The type of error (e.g., "mcp_tool_execution_error").
 * @param content
 *   Sequence of error content items containing the error details.
 */
final case class MCPToolError(
  `type`: String,
  content: Seq[MCPToolErrorContent]
)
