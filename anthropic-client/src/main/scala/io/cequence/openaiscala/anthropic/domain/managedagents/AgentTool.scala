package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.openaiscala.anthropic.domain.tools.CustomTool
import io.cequence.openaiscala.domain.HasType

/**
 * Per-tool (or default) configuration within a managed-agent toolset.
 *
 * @param name
 *   Tool name the config applies to. `None` for a toolset-level `default_config`.
 * @param enabled
 *   Whether the tool is enabled.
 * @param permissionPolicy
 *   Whether the tool runs automatically or pauses for confirmation.
 */
final case class AgentToolConfig(
  name: Option[String] = None,
  enabled: Option[Boolean] = None,
  permissionPolicy: Option[PermissionPolicy] = None
)

/**
 * Tools usable by a Managed Agent. This is a distinct union from the Messages-API
 * [[io.cequence.openaiscala.anthropic.domain.tools.Tool]] union: the managed-agents
 * `mcp_toolset` uses permission-policy configs (vs the Messages-API `deferLoading`/map shape)
 * and `agent_toolset_20260401` is managed-agents only. The `custom` case reuses
 * [[io.cequence.openaiscala.anthropic.domain.tools.CustomTool]].
 */
sealed trait AgentTool extends HasType

object AgentTool {

  /** Built-in agent toolset (bash, edit, read, write, glob, grep, web_fetch, web_search). */
  final case class Toolset(
    configs: Seq[AgentToolConfig] = Nil,
    defaultConfig: Option[AgentToolConfig] = None
  ) extends AgentTool {
    override val `type`: String = "agent_toolset_20260401"
  }

  /** Tools exposed by a connected MCP server, referenced by server name. */
  final case class McpToolset(
    mcpServerName: String,
    configs: Seq[AgentToolConfig] = Nil,
    defaultConfig: Option[AgentToolConfig] = None
  ) extends AgentTool {
    override val `type`: String = "mcp_toolset"
  }

  /** A user-defined tool; reuses the existing [[CustomTool]] shape. */
  final case class Custom(tool: CustomTool) extends AgentTool {
    override val `type`: String = "custom"
  }
}
