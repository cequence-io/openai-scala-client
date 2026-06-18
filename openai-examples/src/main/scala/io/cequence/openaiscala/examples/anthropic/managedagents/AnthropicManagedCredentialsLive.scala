package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.managedagents.CredentialAuth
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateCredentialSettings,
  AnthropicCreateVaultSettings,
  AnthropicUpdateCredentialSettings
}

import scala.concurrent.Future

/**
 * Live end-to-end check of the Credentials API: create a vault, then create -> get -> list ->
 * update -> archive -> delete a static-bearer credential, and delete the vault. Secrets are
 * write-only, so responses never echo the token.
 */
object AnthropicManagedCredentialsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      vault <- service.createVault(
        AnthropicCreateVaultSettings(displayName = "openai-scala-client smoke cred vault")
      )
      _ = println(s"vault=${vault.id}")
      _ <- credentialFlow(vault.id).transformWith { result =>
        service.deleteVault(vault.id).transform(_ => result)
      }
    } yield ()

  private def credentialFlow(vaultId: String): Future[Unit] =
    for {
      cred <- service.createCredential(
        vaultId,
        AnthropicCreateCredentialSettings(
          auth = CredentialAuth.StaticBearer(
            token = "smoke-token-not-real",
            mcpServerUrl = "https://example-server.modelcontextprotocol.io/sse"
          ),
          displayName = Some("smoke credential")
        )
      )
      _ = println(
        s"create: id=${cred.id} authType=${cred.authType} mcpServerUrl=${cred.mcpServerUrl}"
      )

      fetched <- service.getCredential(vaultId, cred.id)
      _ = println(s"get: ${fetched.displayName}")

      listed <- service.listCredentials(vaultId)
      _ = println(s"list: count=${listed.data.size}")

      updated <- service.updateCredential(
        vaultId,
        cred.id,
        AnthropicUpdateCredentialSettings(displayName = Some("smoke credential (v2)"))
      )
      _ = println(s"update: name=${updated.displayName}")

      archived <- service.archiveCredential(vaultId, cred.id)
      _ = println(s"archive: archivedAt=${archived.archivedAt}")

      _ <- service.deleteCredential(vaultId, cred.id)
      _ = println("delete: ok")
    } yield ()
}
