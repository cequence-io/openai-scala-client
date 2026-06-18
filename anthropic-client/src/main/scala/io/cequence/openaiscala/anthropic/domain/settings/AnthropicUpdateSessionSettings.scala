package io.cequence.openaiscala.anthropic.domain.settings

/**
 * Parameters for updating a session (`POST /v1/sessions/{id}`). `None` means "omit
 * (preserve)". `metadata` is a patch: a value of `None` for a key deletes it.
 *
 * (The API also allows session-local `agent.tools`/`agent.mcp_servers` overrides on an idle
 * session; those advanced overrides are not modeled here.)
 */
final case class AnthropicUpdateSessionSettings(
  title: Option[String] = None,
  metadata: Option[Map[String, Option[String]]] = None
)
