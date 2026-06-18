package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.{AgentModelConfig, AgentTool}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicUpdateAgentSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Managed Agents API: create -> get -> list -> update ->
 * list-versions -> archive. Standalone `main` (not the `Example` trait, whose `System.exit`
 * makes sbt swallow output). Requires `ANTHROPIC_API_KEY` with the `managed-agents-2026-04-01`
 * beta enabled. Side-effecting: leaves one archived agent on the account.
 */
object AnthropicManagedAgentsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      println("=== create ===")
      val created = Await.result(
        service.createAgent(
          AnthropicCreateAgentSettings(
            name = "openai-scala-client smoke agent",
            model = AgentModelConfig(NonOpenAIModelId.claude_fable_5),
            system = Some("You are a concise assistant."),
            tools = Seq(AgentTool.Toolset()),
            metadata = Map("source" -> "smoke-test")
          )
        ),
        3.minutes
      )
      println(
        s"id=${created.id} version=${created.version} tools=${created.tools.map(_.`type`)}"
      )

      println("\n=== get ===")
      val fetched = Await.result(service.getAgent(created.id), 1.minute)
      println(s"name=${fetched.name} system=${fetched.system}")

      println("\n=== list (limit 5) ===")
      val listed = Await.result(service.listAgents(limit = Some(5)), 1.minute)
      println(s"count=${listed.data.size} nextPage=${listed.nextPage}")

      println("\n=== update (bump system prompt) ===")
      val updated = Await.result(
        service.updateAgent(
          created.id,
          AnthropicUpdateAgentSettings(
            version = created.version,
            system = Some("You are a concise, friendly assistant.")
          )
        ),
        1.minute
      )
      println(s"new version=${updated.version} system=${updated.system}")

      println("\n=== list versions ===")
      val versions = Await.result(service.listAgentVersions(created.id), 1.minute)
      println(s"versions=${versions.data.map(_.version)}")

      println("\n=== archive ===")
      val archived = Await.result(service.archiveAgent(created.id), 1.minute)
      println(s"archivedAt=${archived.archivedAt}")

      println("\nManaged Agents smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
