package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentModelConfig,
  AgentTool,
  Multiagent
}
import io.cequence.openaiscala.anthropic.domain.skills.SkillParams
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition

/**
 * Parameters for updating a Managed Agent (`POST /v1/agents/{id}`).
 *
 * Each update bumps the version. `None` means "omit (preserve existing)".
 * `tools`/`mcpServers`/ `skills` are full replacements when present. `metadata` is a patch: a
 * value of `None` for a key deletes it.
 *
 * @param version
 *   Current version, for optimistic locking (required).
 */
final case class AnthropicUpdateAgentSettings(
  version: Int,
  name: Option[String] = None,
  description: Option[String] = None,
  system: Option[String] = None,
  model: Option[AgentModelConfig] = None,
  tools: Option[Seq[AgentTool]] = None,
  mcpServers: Option[Seq[MCPServerURLDefinition]] = None,
  skills: Option[Seq[SkillParams]] = None,
  metadata: Option[Map[String, Option[String]]] = None,
  multiagent: Option[Multiagent] = None
)
