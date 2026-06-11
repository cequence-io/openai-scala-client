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
 * Two shapes occur in practice:
 *   - `mcp_tool_execution_error` - carries `content` (the error detail items).
 *   - `mcp_protocol_error` - carries `code` + `message` and NO `content`.
 *
 * @param `type`
 *   The type of error (e.g., "mcp_tool_execution_error" or "mcp_protocol_error").
 * @param content
 *   Sequence of error content items containing the error details (empty for protocol errors).
 * @param code
 *   Numeric error code (present for protocol errors, e.g. -32000).
 * @param message
 *   Error message (present for protocol errors).
 */
final case class MCPToolError(
  `type`: String,
  content: Seq[MCPToolErrorContent] = Nil,
  code: Option[Int] = None,
  message: Option[String] = None
)
