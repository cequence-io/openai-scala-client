package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.openaiscala.anthropic.domain.skills.SkillParams
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition

/**
 * A Managed Agent: a persisted, versioned configuration (model, system prompt, tools, MCP
 * servers, skills) that sessions and deployments reference by id.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
 */
final case class Agent(
  id: String,
  name: String,
  model: AgentModelConfig,
  version: Int,
  description: Option[String] = None,
  system: Option[String] = None,
  tools: Seq[AgentTool] = Nil,
  mcpServers: Seq[MCPServerURLDefinition] = Nil,
  skills: Seq[SkillParams] = Nil,
  metadata: Map[String, String] = Map.empty,
  multiagent: Option[Multiagent] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "agent"
}
