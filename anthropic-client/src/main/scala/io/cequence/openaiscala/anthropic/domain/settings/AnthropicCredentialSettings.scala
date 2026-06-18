package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  CredentialAuth,
  CredentialAuthUpdate
}

/**
 * Parameters for creating a credential (`POST /v1/vaults/{vaultId}/credentials`).
 *
 * @param metadata
 *   Arbitrary key-value metadata (max 16 pairs).
 */
final case class AnthropicCreateCredentialSettings(
  auth: CredentialAuth,
  displayName: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

/**
 * Parameters for updating a credential (`POST /v1/vaults/{vaultId}/credentials/{id}`). `None`
 * means "omit (preserve)". The auth patch ([[CredentialAuthUpdate]]) carries only mutable
 * fields — immutable identifiers such as `mcp_server_url`/`secret_name` cannot be changed.
 *
 * @param metadata
 *   Metadata patch: a value of `None` for a key deletes it.
 */
final case class AnthropicUpdateCredentialSettings(
  auth: Option[CredentialAuthUpdate] = None,
  displayName: Option[String] = None,
  metadata: Option[Map[String, Option[String]]] = None
)
