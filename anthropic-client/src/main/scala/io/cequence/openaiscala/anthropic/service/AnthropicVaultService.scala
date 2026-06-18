package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Credential,
  PagedResponse,
  Vault
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateCredentialSettings,
  AnthropicCreateVaultSettings,
  AnthropicUpdateCredentialSettings,
  AnthropicUpdateVaultSettings
}
import play.api.libs.json.JsObject

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

  /**
   * Creates a vault (`POST /v1/vaults`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
   */
  def createVault(settings: AnthropicCreateVaultSettings): Future[Vault]

  /**
   * Lists vaults (`GET /v1/vaults`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
   */
  def listVaults(
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Vault]]

  /**
   * Retrieves a vault (`GET /v1/vaults/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
   */
  def getVault(vaultId: String): Future[Vault]

  /**
   * Updates a vault (`POST /v1/vaults/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
   */
  def updateVault(
    vaultId: String,
    settings: AnthropicUpdateVaultSettings
  ): Future[Vault]

  /**
   * Deletes a vault (`DELETE /v1/vaults/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
   */
  def deleteVault(vaultId: String): Future[Unit]

  /**
   * Archives a vault (`POST /v1/vaults/{id}/archive`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults">Anthropic Vaults API</a>
   */
  def archiveVault(vaultId: String): Future[Vault]

  // -- Credentials (vault sub-resource) --

  /**
   * Creates a credential (`POST /v1/vaults/{vaultId}/credentials`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def createCredential(
    vaultId: String,
    settings: AnthropicCreateCredentialSettings
  ): Future[Credential]

  /**
   * Lists credentials in a vault (`GET /v1/vaults/{vaultId}/credentials`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def listCredentials(
    vaultId: String,
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Credential]]

  /**
   * Retrieves a credential (`GET /v1/vaults/{vaultId}/credentials/{credentialId}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def getCredential(
    vaultId: String,
    credentialId: String
  ): Future[Credential]

  /**
   * Updates a credential (`POST /v1/vaults/{vaultId}/credentials/{credentialId}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def updateCredential(
    vaultId: String,
    credentialId: String,
    settings: AnthropicUpdateCredentialSettings
  ): Future[Credential]

  /**
   * Deletes a credential (`DELETE /v1/vaults/{vaultId}/credentials/{credentialId}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def deleteCredential(
    vaultId: String,
    credentialId: String
  ): Future[Unit]

  /**
   * Archives a credential (`POST /v1/vaults/{vaultId}/credentials/{credentialId}/archive`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def archiveCredential(
    vaultId: String,
    credentialId: String
  ): Future[Credential]

  /**
   * Validates an MCP OAuth credential (`POST
   * /v1/vaults/{vaultId}/credentials/{credentialId}/mcp_oauth_validate`). The probe result
   * schema is detailed and unpublished, so the raw JSON response is returned.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
   *   Credentials API</a>
   */
  def mcpOAuthValidateCredential(
    vaultId: String,
    credentialId: String
  ): Future[JsObject]
}
