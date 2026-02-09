package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.openaiscala.anthropic.domain.CacheControl

/**
 * MCP Toolset configuration for controlling which tools from an MCP server are enabled and how
 * they should be configured. Lives in the tools array.
 *
 * Configuration values merge with this precedence (highest to lowest):
 *   - Tool-specific settings in `configs`
 *   - Set-level `defaultConfig`
 *   - System defaults (enabled: true, deferLoading: false)
 *
 * @param mcpServerName
 *   Must match a server name defined in the mcp_servers array.
 * @param defaultConfig
 *   Default configuration applied to all tools in this set. Individual tool configs in
 *   `configs` will override these defaults.
 * @param configs
 *   Per-tool configuration overrides. Keys are tool names, values are configuration objects.
 * @param cacheControl
 *   Cache breakpoint configuration for this toolset.
 *
 * @see
 *   [[https://platform.claude.com/docs/en/agents-and-tools/mcp-connector MCP Connector Documentation]]
 */
case class MCPToolset(
  mcpServerName: String,
  defaultConfig: Option[MCPToolConfig] = None,
  configs: Map[String, MCPToolConfig] = Map.empty,
  cacheControl: Option[CacheControl] = None
) extends Tool {
  override val `type`: String = "mcp_toolset"
  override val name: String = mcpServerName
}

/**
 * Configuration for individual MCP tools.
 *
 * @param enabled
 *   Whether this tool is enabled. Defaults to true if not specified.
 * @param deferLoading
 *   If true, tool description is not sent to the model initially. Used with Tool Search Tool.
 *   Defaults to false if not specified.
 */
case class MCPToolConfig(
  enabled: Option[Boolean] = None,
  deferLoading: Option[Boolean] = None
)

object MCPToolConfig {

  /**
   * Create a config with the tool enabled.
   */
  def enabled: MCPToolConfig = MCPToolConfig(enabled = Some(true))

  /**
   * Create a config with the tool disabled.
   */
  def disabled: MCPToolConfig = MCPToolConfig(enabled = Some(false))

  /**
   * Create a config with deferred loading enabled.
   */
  def deferred: MCPToolConfig = MCPToolConfig(deferLoading = Some(true))

  /**
   * Create a config with the tool enabled and deferred loading.
   */
  def enabledDeferred: MCPToolConfig =
    MCPToolConfig(enabled = Some(true), deferLoading = Some(true))

  /**
   * Create a config with the tool disabled and deferred loading.
   */
  def disabledDeferred: MCPToolConfig =
    MCPToolConfig(enabled = Some(false), deferLoading = Some(true))
}
