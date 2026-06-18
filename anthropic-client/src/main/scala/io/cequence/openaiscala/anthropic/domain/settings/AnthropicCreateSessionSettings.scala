package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.SessionResource

/**
 * Parameters for creating a session (`POST /v1/sessions`).
 *
 * @param agentId
 *   The agent to run (required).
 * @param environmentId
 *   The environment to run it in (required).
 * @param agentVersion
 *   Pin a specific agent version; latest if omitted.
 * @param resources
 *   Files, GitHub repos, and memory stores to mount.
 * @param vaultIds
 *   Vaults supplying MCP credentials.
 */
final case class AnthropicCreateSessionSettings(
  agentId: String,
  environmentId: String,
  agentVersion: Option[Int] = None,
  title: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  resources: Seq[SessionResource] = Nil,
  vaultIds: Seq[String] = Nil
)
