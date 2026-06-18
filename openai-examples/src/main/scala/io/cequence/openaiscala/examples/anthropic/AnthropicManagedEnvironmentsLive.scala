package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  EnvironmentConfig,
  Networking,
  Packages
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateEnvironmentSettings,
  AnthropicUpdateEnvironmentSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Environments API: create -> get -> list -> update -> archive ->
 * delete. Standalone `main`; requires `ANTHROPIC_API_KEY` with the `managed-agents-2026-04-01`
 * beta. Side-effecting: creates then deletes a cloud environment.
 */
object AnthropicManagedEnvironmentsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      println("=== create ===")
      val created = Await.result(
        service.createEnvironment(
          AnthropicCreateEnvironmentSettings(
            name = "openai-scala-client smoke env",
            config = Some(
              EnvironmentConfig.Cloud(
                networking = Some(Networking.Unrestricted),
                packages = Some(Packages(pip = Seq("pandas")))
              )
            ),
            description = Some("smoke-test env")
          )
        ),
        2.minutes
      )
      println(s"id=${created.id} config=${created.config}")

      println("\n=== get ===")
      println(Await.result(service.getEnvironment(created.id), 1.minute).name)

      println("\n=== list ===")
      val listed = Await.result(service.listEnvironments(limit = Some(5)), 1.minute)
      println(s"count=${listed.data.size} nextPage=${listed.nextPage}")

      println("\n=== update (description) ===")
      val updated = Await.result(
        service.updateEnvironment(
          created.id,
          AnthropicUpdateEnvironmentSettings(description = Some("updated smoke-test env"))
        ),
        1.minute
      )
      println(s"description=${updated.description}")

      println("\n=== archive ===")
      println(
        s"archivedAt=${Await.result(service.archiveEnvironment(created.id), 1.minute).archivedAt}"
      )

      println("\n=== delete ===")
      println(Await.result(service.deleteEnvironment(created.id), 1.minute).`type`)

      println("\nEnvironments smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
