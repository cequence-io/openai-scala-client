package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{PagedResponse, Vault}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateVaultSettings,
  AnthropicUpdateVaultSettings
}

import scala.concurrent.Future

/**
 * Anthropic Managed Agents API — vault management. Vaults store credentials (e.g. MCP OAuth
 * tokens) attached to sessions via `vaultIds`. Requires the `managed-agents-2026-04-01` beta;
 * not available on Bedrock.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
 */
trait AnthropicVaultService {

  /** Creates a vault (`POST /v1/vaults`). */
  def createVault(settings: AnthropicCreateVaultSettings): Future[Vault]

  /** Lists vaults (`GET /v1/vaults`). */
  def listVaults(
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Vault]]

  /** Retrieves a vault (`GET /v1/vaults/{id}`). */
  def getVault(vaultId: String): Future[Vault]

  /** Updates a vault (`POST /v1/vaults/{id}`). */
  def updateVault(
    vaultId: String,
    settings: AnthropicUpdateVaultSettings
  ): Future[Vault]

  /** Deletes a vault (`DELETE /v1/vaults/{id}`). */
  def deleteVault(vaultId: String): Future[Unit]

  /** Archives a vault (`POST /v1/vaults/{id}/archive`). */
  def archiveVault(vaultId: String): Future[Vault]
}
