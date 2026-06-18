package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.SessionAgentOverride

/**
 * Parameters for updating a session (`POST /v1/sessions/{id}`). `None` means "omit
 * (preserve)". `metadata` is a patch: a value of `None` for a key deletes it.
 *
 * @param agent
 *   Session-local agent overrides (`tools`/`mcp_servers`), allowed only on an idle session.
 * @param vaultIds
 *   Replaces the vaults supplying MCP credentials.
 */
final case class AnthropicUpdateSessionSettings(
  title: Option[String] = None,
  metadata: Option[Map[String, Option[String]]] = None,
  agent: Option[SessionAgentOverride] = None,
  vaultIds: Option[Seq[String]] = None
)
