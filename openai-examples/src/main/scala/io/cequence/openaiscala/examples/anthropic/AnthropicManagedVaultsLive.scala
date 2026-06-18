package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateVaultSettings,
  AnthropicUpdateVaultSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Vaults API: create -> get -> list -> update -> archive ->
 * delete. Requires `ANTHROPIC_API_KEY` with the `managed-agents-2026-04-01` beta.
 */
object AnthropicManagedVaultsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      println("=== create ===")
      val vault = Await.result(
        service.createVault(
          AnthropicCreateVaultSettings(
            displayName = "openai-scala-client smoke vault",
            metadata = Map("source" -> "smoke-test")
          )
        ),
        2.minutes
      )
      println(s"id=${vault.id} name=${vault.displayName}")

      println("\n=== get ===")
      println(Await.result(service.getVault(vault.id), 1.minute).displayName)

      println("\n=== list ===")
      val listed = Await.result(service.listVaults(limit = Some(5)), 1.minute)
      println(s"count=${listed.data.size} nextPage=${listed.nextPage}")

      println("\n=== update ===")
      val updated = Await.result(
        service.updateVault(
          vault.id,
          AnthropicUpdateVaultSettings(displayName =
            Some("openai-scala-client smoke vault (v2)")
          )
        ),
        1.minute
      )
      println(s"name=${updated.displayName}")

      println("\n=== archive ===")
      println(
        s"archivedAt=${Await.result(service.archiveVault(vault.id), 1.minute).archivedAt}"
      )

      println("\n=== delete ===")
      Await.result(service.deleteVault(vault.id), 1.minute)
      println("deleted")

      println("\nVaults smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
