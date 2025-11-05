package io.cequence.openaiscala.domain.responsesapi.tools.mcp

import io.cequence.openaiscala.domain.responsesapi.tools.Tool
import io.cequence.wsclient.domain.EnumValue

/**
 * Give the model access to additional tools via remote Model Context Protocol (MCP) servers.
 * One of serverUrl or connectorId must be provided.
 *
 * @param serverLabel
 *   A label for this MCP server, used to identify it in tool calls.
 * @param allowedTools
 *   Optional list of allowed tool names or a filter object.
 * @param authorization
 *   Optional OAuth access token for authentication with a remote MCP server.
 * @param connectorId
 *   Optional identifier for service connectors. One of server_url or connector_id must be
 *   provided.
 * @param headers
 *   Optional HTTP headers to send to the MCP server.
 * @param requireApproval
 *   Specify which of the MCP server's tools require approval. Defaults to always.
 * @param serverDescription
 *   Optional description of the MCP server.
 * @param serverUrl
 *   Optional URL for the MCP server. One of server_url or connector_id must be provided.
 */
case class MCPTool(
  serverLabel: String,
  serverUrl: Option[String] = None,
  connectorId: Option[String] = None,
  authorization: Option[String] = None,
  headers: Option[Map[String, String]] = None,
  allowedTools: Option[MCPAllowedTools] = None,
  requireApproval: Option[MCPRequireApproval] = None,
  serverDescription: Option[String] = None
) extends Tool {
  override val `type`: String = "mcp"
}

/**
 * Predefined connector IDs for service connectors.
 */
object ConnectorId {
  val Dropbox: String = "connector_dropbox"
  val Gmail: String = "connector_gmail"
  val GoogleCalendar: String = "connector_googlecalendar"
  val GoogleDrive: String = "connector_googledrive"
  val MicrosoftTeams: String = "connector_microsoftteams"
  val OutlookCalendar: String = "connector_outlookcalendar"
  val OutlookEmail: String = "connector_outlookemail"
  val SharePoint: String = "connector_sharepoint"
}

/**
 * Allowed tools specification for MCP servers.
 */
sealed trait MCPAllowedTools

object MCPAllowedTools {

  /**
   * List of specific tool names that are allowed.
   */
  case class ToolNames(names: Seq[String]) extends MCPAllowedTools

  /**
   * Filter object for allowed tools.
   *
   * @param readOnly
   *   Indicates whether tools must be read-only.
   * @param toolNames
   *   Optional list of tool names.
   */
  case class Filter(
    readOnly: Option[Boolean] = None,
    toolNames: Option[Seq[String]] = None
  ) extends MCPAllowedTools
}

/**
 * Approval requirement specification for MCP tools.
 */
sealed trait MCPRequireApproval

object MCPRequireApproval {

  /**
   * Simple approval policy: always or never.
   */
  sealed trait Setting extends MCPRequireApproval with EnumValue {
    override def toString: String = super.toString.toLowerCase
  }

  object Setting {
    case object Always extends Setting
    case object Never extends Setting

    def values = Seq(Always, Never)
  }

  /**
   * Filter-based approval requirements.
   *
   * @param always
   *   Optional filter object to specify which tools always require approval.
   * @param never
   *   Optional filter object to specify which tools never require approval.
   */
  case class Filter(
    always: Option[MCPToolFilter] = None,
    never: Option[MCPToolFilter] = None
  ) extends MCPRequireApproval
}

/**
 * Filter specification for MCP tools.
 *
 * @param readOnly
 *   Indicates whether tools must be read-only.
 * @param toolNames
 *   Optional list of tool names.
 */
case class MCPToolFilter(
  readOnly: Option[Boolean] = None,
  toolNames: Option[Seq[String]] = None
)
