package io.cequence.openaiscala.anthropic.domain.tools

/**
 * Configuration for MCP tool filtering and enabling.
 *
 * @param allowedTools
 *   List of allowed tool names. If specified, only these tools will be available.
 * @param enabled
 *   Whether the tool configuration is enabled.
 */
case class MCPToolConfiguration(
  allowedTools: Seq[String] = Nil,
  enabled: Option[Boolean] = None
)

/**
 * MCP Server URL Definition for connecting to an MCP server via URL. Type is always "url".
 *
 * @param name
 *   Name of the MCP server.
 * @param url
 *   URL of the MCP server to connect to.
 * @param authorizationToken
 *   Optional authorization token for authenticating with the MCP server.
 * @param toolConfiguration
 *   Optional configuration for tool filtering and enabling.
 */
case class MCPServerURLDefinition(
  name: String,
  url: String,
  authorizationToken: Option[String] = None,
  toolConfiguration: Option[MCPToolConfiguration] = None
) extends Tool {
  override val `type`: String = "url"
}
