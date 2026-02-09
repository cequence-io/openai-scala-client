package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.openaiscala.anthropic.domain.{CacheControl, CacheTTL}
import io.cequence.openaiscala.domain.{HasType, JsonSchema}

/**
 * Base trait for all Anthropic tools.
 */
trait Tool extends HasType {
  def name: String
}

/**
 * Convenient factory methods for creating Anthropic tools with common configurations.
 *
 * This object provides shortcut methods to quickly create tool instances without having to
 * specify all parameters explicitly.
 */
object Tool {

  // BASH TOOL

  /**
   * Create a Bash tool with the latest version.
   */
  def bash(): BashTool = BashTool()

  /**
   * Create a Bash tool with a specific version.
   */
  def bash(version: BashToolType): BashTool = BashTool(version)

  // CODE EXECUTION TOOL

  /**
   * Create a Code Execution tool with the latest version.
   */
  def codeExecution(): CodeExecutionTool = CodeExecutionTool()

  /**
   * Create a Code Execution tool with a specific version.
   */
  def codeExecution(version: CodeExecutionToolType): CodeExecutionTool =
    CodeExecutionTool(version)

  // COMPUTER USE TOOL

  /**
   * Create a Computer Use tool with standard 1024x768 display.
   */
  def computer(): ComputerUseTool = ComputerUseTool(
    displayHeightPx = 768,
    displayWidthPx = 1024,
    `type` = ComputerUseToolType.computer_20250124,
    displayNumber = None
  )

  /**
   * Create a Computer Use tool with custom display dimensions.
   */
  def computer(
    displayHeightPx: Int,
    displayWidthPx: Int,
    version: ComputerUseToolType = ComputerUseToolType.computer_20250124,
    displayNumber: Option[Int] = None
  ): ComputerUseTool =
    ComputerUseTool(displayHeightPx, displayWidthPx, version, displayNumber)

  /**
   * Create a Computer Use tool with HD display (1920x1080).
   */
  def computerHD(): ComputerUseTool =
    ComputerUseTool(
      displayHeightPx = 1080,
      displayWidthPx = 1920,
      `type` = ComputerUseToolType.computer_20250124,
      displayNumber = None
    )

  // CUSTOM TOOL

  /**
   * Create a Custom tool with name and schema.
   */
  def custom(
    name: String,
    inputSchema: JsonSchema,
    description: Option[String] = None
  ): CustomTool = CustomTool(name, inputSchema, description)

  // MEMORY TOOL

  /**
   * Create a Memory tool for persisting information across conversations.
   */
  def memory(): MemoryTool = MemoryTool()

  // TEXT EDITOR TOOL

  /**
   * Create a Text Editor tool with the latest version.
   */
  def textEditor(): TextEditorTool = TextEditorTool()

  /**
   * Create a Text Editor tool with a specific version.
   */
  def textEditor(version: TextEditorToolType): TextEditorTool =
    TextEditorTool(version)

  /**
   * Create a Text Editor tool with cache control.
   */
  def textEditor(
    version: TextEditorToolType,
    cacheControl: CacheControl
  ): TextEditorTool = TextEditorTool(version, Some(cacheControl))

  /**
   * Create a Text Editor tool with cache control and TTL.
   */
  def textEditor(
    version: TextEditorToolType,
    cacheTTL: CacheTTL
  ): TextEditorTool =
    TextEditorTool(version, Some(CacheControl.Ephemeral(Some(cacheTTL))))

  // WEB SEARCH TOOL

  /**
   * Create a Web Search tool with optional configuration.
   *
   * @param allowedDomains
   *   Domains to allow for web search results
   * @param blockedDomains
   *   Domains to block from web search results
   * @param maxUses
   *   Maximum number of web searches allowed
   * @param userLocation
   *   User location for search localization
   */
  def webSearch(
    allowedDomains: Seq[String] = Nil,
    blockedDomains: Seq[String] = Nil,
    maxUses: Option[Int] = None,
    userLocation: Option[UserLocation] = None
  ): WebSearchTool =
    WebSearchTool(
      allowedDomains = allowedDomains,
      blockedDomains = blockedDomains,
      maxUses = maxUses,
      userLocation = userLocation
    )

  // WEB FETCH TOOL

  /**
   * Create a Web Fetch tool with optional configuration.
   *
   * @param allowedDomains
   *   Domains to allow for web fetch
   * @param blockedDomains
   *   Domains to block from web fetch
   * @param citations
   *   Whether to enable citations
   * @param maxContentTokens
   *   Maximum content tokens to fetch
   * @param maxUses
   *   Maximum number of web fetches allowed
   */
  def webFetch(
    allowedDomains: Seq[String] = Nil,
    blockedDomains: Seq[String] = Nil,
    citations: Option[Citations] = None,
    maxContentTokens: Option[Int] = None,
    maxUses: Option[Int] = None
  ): WebFetchTool =
    WebFetchTool(
      allowedDomains = allowedDomains,
      blockedDomains = blockedDomains,
      citations = citations,
      maxContentTokens = maxContentTokens,
      maxUses = maxUses
    )

  // MCP SERVER URL DEFINITION

  /**
   * Create an MCP Server URL definition with authorization token.
   */
  def mcpServerWithAuth(
    name: String,
    url: String,
    authToken: Option[String]
  ): MCPServerURLDefinition =
    MCPServerURLDefinition(name, url, authToken)

  /**
   * Create an MCP Server URL definition with auth token and allowed tools.
   */
  def mcpServer(
    name: String,
    url: String,
    authToken: Option[String],
    allowedTools: Seq[String]
  ): MCPServerURLDefinition =
    MCPServerURLDefinition(
      name,
      url,
      authToken,
      Some(MCPToolConfiguration(allowedTools = allowedTools, enabled = Some(true)))
    )

  // MCP TOOLSET

  /**
   * Create an MCP Toolset for configuring tools from an MCP server.
   *
   * @param mcpServerName
   *   Must match a server name defined in the mcp_servers array
   */
  def mcpToolset(mcpServerName: String): MCPToolset =
    MCPToolset(mcpServerName)

  /**
   * Create an MCP Toolset with default configuration for all tools.
   *
   * @param mcpServerName
   *   Must match a server name defined in the mcp_servers array
   * @param defaultConfig
   *   Default configuration applied to all tools
   */
  def mcpToolset(
    mcpServerName: String,
    defaultConfig: MCPToolConfig
  ): MCPToolset =
    MCPToolset(mcpServerName, defaultConfig = Some(defaultConfig))

  /**
   * Create an MCP Toolset with per-tool configuration overrides.
   *
   * @param mcpServerName
   *   Must match a server name defined in the mcp_servers array
   * @param configs
   *   Per-tool configuration overrides (tool name -> config)
   */
  def mcpToolset(
    mcpServerName: String,
    configs: Map[String, MCPToolConfig]
  ): MCPToolset =
    MCPToolset(mcpServerName, configs = configs)

  /**
   * Create an MCP Toolset with default config and per-tool overrides.
   *
   * @param mcpServerName
   *   Must match a server name defined in the mcp_servers array
   * @param defaultConfig
   *   Default configuration applied to all tools
   * @param configs
   *   Per-tool configuration overrides (tool name -> config)
   */
  def mcpToolset(
    mcpServerName: String,
    defaultConfig: MCPToolConfig,
    configs: Map[String, MCPToolConfig]
  ): MCPToolset =
    MCPToolset(mcpServerName, defaultConfig = Some(defaultConfig), configs = configs)

  // USER LOCATION

  /**
   * Create a user location with city and country.
   */
  def userLocation(
    city: String,
    country: String
  ): UserLocation =
    UserLocation(city = Some(city), country = Some(country))

  /**
   * Create a user location with city, country, and timezone.
   */
  def userLocation(
    city: String,
    country: String,
    timezone: String
  ): UserLocation =
    UserLocation(
      city = Some(city),
      country = Some(country),
      timezone = Some(timezone)
    )
}
