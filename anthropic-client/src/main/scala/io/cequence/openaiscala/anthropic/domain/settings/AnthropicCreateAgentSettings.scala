package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentModelConfig,
  AgentTool,
  Multiagent
}
import io.cequence.openaiscala.anthropic.domain.skills.SkillParams
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition

/**
 * Parameters for creating a Managed Agent (`POST /v1/agents`).
 *
 * @param name
 *   Human-readable name (required).
 * @param model
 *   Model configuration (required).
 */
final case class AnthropicCreateAgentSettings(
  name: String,
  model: AgentModelConfig,
  description: Option[String] = None,
  system: Option[String] = None,
  tools: Seq[AgentTool] = Nil,
  mcpServers: Seq[MCPServerURLDefinition] = Nil,
  skills: Seq[SkillParams] = Nil,
  metadata: Map[String, String] = Map.empty,
  multiagent: Option[Multiagent] = None
)
