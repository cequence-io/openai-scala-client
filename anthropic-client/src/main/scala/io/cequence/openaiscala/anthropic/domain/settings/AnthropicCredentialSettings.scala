package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.CredentialAuth

/** Parameters for creating a credential (`POST /v1/vaults/{vaultId}/credentials`). */
final case class AnthropicCreateCredentialSettings(
  auth: CredentialAuth,
  displayName: Option[String] = None
)

/**
 * Parameters for updating a credential (`POST /v1/vaults/{vaultId}/credentials/{id}`). `None`
 * means "omit (preserve)".
 */
final case class AnthropicUpdateCredentialSettings(
  auth: Option[CredentialAuth] = None,
  displayName: Option[String] = None
)
