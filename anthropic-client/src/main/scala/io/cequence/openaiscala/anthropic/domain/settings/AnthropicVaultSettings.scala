package io.cequence.openaiscala.anthropic.domain.settings

/** Parameters for creating a vault (`POST /v1/vaults`). */
final case class AnthropicCreateVaultSettings(
  displayName: String,
  metadata: Map[String, String] = Map.empty
)

/**
 * Parameters for updating a vault (`POST /v1/vaults/{id}`). `None` means "omit (preserve)".
 * `metadata` is a patch: a value of `None` for a key deletes it.
 */
final case class AnthropicUpdateVaultSettings(
  displayName: Option[String] = None,
  metadata: Option[Map[String, Option[String]]] = None
)
