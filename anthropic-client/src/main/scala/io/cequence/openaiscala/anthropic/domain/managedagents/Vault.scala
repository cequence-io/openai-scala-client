package io.cequence.openaiscala.anthropic.domain.managedagents

/**
 * A vault stores credentials (e.g. MCP OAuth tokens) that agents use during sessions. Attach
 * to a session via `vaultIds`.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
 */
final case class Vault(
  id: String,
  displayName: String,
  metadata: Map[String, String] = Map.empty,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "vault"
}
