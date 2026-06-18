package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.CredentialAuth
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateCredentialSettings,
  AnthropicCreateVaultSettings,
  AnthropicUpdateCredentialSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Credentials API: create a vault, then create -> get -> list ->
 * update -> archive -> delete a static-bearer credential, and delete the vault. Secrets are
 * write-only, so responses never echo the token. Requires `ANTHROPIC_API_KEY` with the
 * `managed-agents-2026-04-01` beta.
 */
object AnthropicManagedCredentialsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      val vault = Await.result(
        service.createVault(
          AnthropicCreateVaultSettings(displayName = "openai-scala-client smoke cred vault")
        ),
        2.minutes
      )
      println(s"vault=${vault.id}")

      try {
        println("\n=== create credential (static bearer) ===")
        val cred = Await.result(
          service.createCredential(
            vault.id,
            AnthropicCreateCredentialSettings(
              auth = CredentialAuth.StaticBearer(
                token = "smoke-token-not-real",
                mcpServerUrl = "https://example-server.modelcontextprotocol.io/sse"
              ),
              displayName = Some("smoke credential")
            )
          ),
          1.minute
        )
        println(s"id=${cred.id} authType=${cred.authType} mcpServerUrl=${cred.mcpServerUrl}")

        println("\n=== get ===")
        println(Await.result(service.getCredential(vault.id, cred.id), 1.minute).displayName)

        println("\n=== list ===")
        println(
          s"count=${Await.result(service.listCredentials(vault.id), 1.minute).data.size}"
        )

        println("\n=== update display name ===")
        val updated = Await.result(
          service.updateCredential(
            vault.id,
            cred.id,
            AnthropicUpdateCredentialSettings(displayName = Some("smoke credential (v2)"))
          ),
          1.minute
        )
        println(s"name=${updated.displayName}")

        println("\n=== archive ===")
        println(
          s"archivedAt=${Await.result(service.archiveCredential(vault.id, cred.id), 1.minute).archivedAt}"
        )

        println("\n=== delete ===")
        Await.result(service.deleteCredential(vault.id, cred.id), 1.minute)
        println("deleted")
      } finally {
        Await.result(service.deleteVault(vault.id), 1.minute)
      }

      println("\nCredentials smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
