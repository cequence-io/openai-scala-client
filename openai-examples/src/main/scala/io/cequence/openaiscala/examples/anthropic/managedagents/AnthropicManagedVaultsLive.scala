package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateVaultSettings,
  AnthropicUpdateVaultSettings
}

import scala.concurrent.Future

/**
 * Live end-to-end check of the Vaults API: create -> get -> list -> update -> archive ->
 * delete.
 */
object AnthropicManagedVaultsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      vault <- service.createVault(
        AnthropicCreateVaultSettings(
          displayName = "openai-scala-client smoke vault",
          metadata = Map("source" -> "smoke-test")
        )
      )
      _ = println(s"created: id=${vault.id} name=${vault.displayName}")

      fetched <- service.getVault(vault.id)
      _ = println(s"get: ${fetched.displayName}")

      listed <- service.listVaults(limit = Some(5))
      _ = println(s"list: count=${listed.data.size} nextPage=${listed.nextPage}")

      updated <- service.updateVault(
        vault.id,
        AnthropicUpdateVaultSettings(displayName =
          Some("openai-scala-client smoke vault (v2)")
        )
      )
      _ = println(s"update: name=${updated.displayName}")

      archived <- service.archiveVault(vault.id)
      _ = println(s"archive: archivedAt=${archived.archivedAt}")

      _ <- service.deleteVault(vault.id)
      _ = println("delete: ok")
    } yield ()
}
