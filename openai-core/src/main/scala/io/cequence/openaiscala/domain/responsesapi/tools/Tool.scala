package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.{HasType, JsonSchema}
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.{
  MCPAllowedTools,
  MCPRequireApproval,
  MCPTool
}

trait Tool extends HasType

object Tool {

  // FunctionTool shortcuts
  def function(
    name: String,
    parameters: JsonSchema,
    strict: Boolean = true,
    description: Option[String] = None
  ): FunctionTool =
    FunctionTool(name, parameters, strict, description)

  // FileSearchTool shortcuts
  def fileSearch(
    vectorStoreIds: Seq[String] = Nil,
    filters: Option[FileFilter] = None,
    maxNumResults: Option[Int] = None,
    rankingOptions: Option[FileSearchRankingOptions] = None
  ): FileSearchTool =
    FileSearchTool(vectorStoreIds, filters, maxNumResults, rankingOptions)

  // WebSearchTool shortcuts
  def webSearch(
    filters: Option[WebSearchFilters] = None,
    searchContextSize: Option[String] = None,
    userLocation: Option[WebSearchUserLocation] = None,
    `type`: WebSearchType = WebSearchType.WebSearch
  ): WebSearchTool =
    WebSearchTool(filters, searchContextSize, userLocation, `type`)

  def webSearch(allowedDomains: Seq[String]): WebSearchTool =
    WebSearchTool(filters = Some(WebSearchFilters(allowedDomains)))

  // ComputerUseTool shortcuts
  def computerUse(
    displayHeight: Int,
    displayWidth: Int,
    environment: String = "docker"
  ): ComputerUseTool =
    ComputerUseTool(displayHeight, displayWidth, environment)

  def computerUse: ComputerUseTool =
    ComputerUseTool(displayHeight = 1024, displayWidth = 768, environment = "docker")

  // CodeInterpreterTool shortcuts
  def codeInterpreter(fileIds: Seq[String] = Nil): CodeInterpreterTool =
    CodeInterpreterTool(CodeInterpreterContainer.Auto(fileIds))

  def codeInterpreter(containerId: String): CodeInterpreterTool =
    CodeInterpreterTool(CodeInterpreterContainer.ContainerId(containerId))

  // ImageGenerationTool shortcuts
  def imageGeneration(
    background: Option[ImageGenerationBackground] = None,
    inputFidelity: Option[String] = None,
    inputImageMask: Option[InputImageMask] = None,
    model: Option[String] = None,
    moderation: Option[String] = None,
    outputCompression: Option[Int] = None,
    outputFormat: Option[String] = None,
    partialImages: Option[Int] = None,
    quality: Option[String] = None,
    size: Option[String] = None
  ): ImageGenerationTool =
    ImageGenerationTool(
      background,
      inputFidelity,
      inputImageMask,
      model,
      moderation,
      outputCompression,
      outputFormat,
      partialImages,
      quality,
      size
    )

  // LocalShellTool shortcut
  def localShell: LocalShellTool.type = LocalShellTool

  // CustomTool shortcuts
  def custom(
    name: String,
    description: Option[String] = None,
    format: Option[CustomToolFormat] = None
  ): CustomTool =
    CustomTool(name, description, format)

  def custom(
    name: String,
    description: String
  ): CustomTool =
    CustomTool(name, Some(description), None)

  def customWithGrammar(
    name: String,
    grammarDefinition: String,
    grammarSyntax: GrammarSyntax,
    description: Option[String] = None
  ): CustomTool =
    CustomTool(name, description, Some(GrammarFormat(grammarDefinition, grammarSyntax)))

  // MCPTool shortcuts
  def mcp(
    serverLabel: String,
    serverUrl: Option[String] = None,
    connectorId: Option[String] = None,
    authorization: Option[String] = None,
    headers: Option[Map[String, String]] = None,
    allowedTools: Option[MCPAllowedTools] = None,
    requireApproval: Option[MCPRequireApproval] = None,
    serverDescription: Option[String] = None
  ): MCPTool =
    MCPTool(
      serverLabel,
      serverUrl,
      connectorId,
      authorization,
      headers,
      allowedTools,
      requireApproval,
      serverDescription
    )

  def mcp(
    serverLabel: String,
    serverUrl: String
  ): MCPTool =
    MCPTool(serverLabel, Some(serverUrl))

  def mcpWithConnector(
    serverLabel: String,
    connectorId: String
  ): MCPTool =
    MCPTool(serverLabel, connectorId = Some(connectorId))
}
