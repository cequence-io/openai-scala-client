package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentModelConfig,
  AgentTool,
  EnvironmentConfig
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicCreateEnvironmentSettings,
  AnthropicCreateSessionSettings,
  AnthropicUpdateSessionSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Sessions API: create an agent + environment, then create -> get
 * -> list -> update -> list events -> list resources -> archive -> delete a session, and clean
 * up the agent and environment. The model is invoked only if events are sent, which this
 * example deliberately avoids. Requires `ANTHROPIC_API_KEY` with the
 * `managed-agents-2026-04-01` beta.
 */
object AnthropicManagedSessionsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      val agent = Await.result(
        service.createAgent(
          AnthropicCreateAgentSettings(
            name = "openai-scala-client smoke session agent",
            model = AgentModelConfig(NonOpenAIModelId.claude_opus_4_6),
            system = Some("You are a concise assistant."),
            tools = Seq(AgentTool.Toolset())
          )
        ),
        2.minutes
      )
      val env = Await.result(
        service.createEnvironment(
          AnthropicCreateEnvironmentSettings(
            name = "openai-scala-client smoke session env",
            config = Some(EnvironmentConfig.Cloud())
          )
        ),
        2.minutes
      )
      println(s"agent=${agent.id} env=${env.id}")

      try {
        println("\n=== create session ===")
        val session = Await.result(
          service.createSession(
            AnthropicCreateSessionSettings(
              agentId = agent.id,
              environmentId = env.id,
              title = Some("smoke session")
            )
          ),
          3.minutes
        )
        println(s"id=${session.id} status=${session.status}")

        println("\n=== get ===")
        println(Await.result(service.getSession(session.id), 1.minute).title)

        println("\n=== list ===")
        val listed = Await.result(service.listSessions(limit = Some(5)), 1.minute)
        println(s"count=${listed.data.size} nextPage=${listed.nextPage}")

        println("\n=== update title ===")
        val updated = Await.result(
          service.updateSession(
            session.id,
            AnthropicUpdateSessionSettings(title = Some("smoke session (updated)"))
          ),
          1.minute
        )
        println(s"title=${updated.title}")

        println("\n=== list events ===")
        val events =
          Await.result(service.listSessionEvents(session.id, limit = Some(10)), 1.minute)
        println(s"events=${events.data.size} (types=${events.data.map(_.`type`).distinct})")

        println("\n=== list resources ===")
        println(
          s"resources=${Await.result(service.listSessionResources(session.id), 1.minute).data.size}"
        )

        println("\n=== archive + delete ===")
        Await.result(service.archiveSession(session.id), 1.minute)
        println(Await.result(service.deleteSession(session.id), 1.minute).`type`)
      } finally {
        Await.result(service.archiveAgent(agent.id), 1.minute)
        Await.result(service.deleteEnvironment(env.id), 1.minute)
      }

      println("\nSessions smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
