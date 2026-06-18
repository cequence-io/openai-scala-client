package io.cequence.openaiscala.examples.anthropic.managedagents

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
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.Future

/**
 * Live end-to-end check of the Sessions API: create an agent + environment, then create -> get
 * -> list -> update -> list events -> list resources -> archive -> delete a session, and clean
 * up the agent and environment. The model is invoked only if events are sent, which this
 * example deliberately avoids.
 */
object AnthropicManagedSessionsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      agent <- service.createAgent(
        AnthropicCreateAgentSettings(
          name = "openai-scala-client smoke session agent",
          model = AgentModelConfig(NonOpenAIModelId.claude_opus_4_6),
          system = Some("You are a concise assistant."),
          tools = Seq(AgentTool.Toolset())
        )
      )
      env <- service.createEnvironment(
        AnthropicCreateEnvironmentSettings(
          name = "openai-scala-client smoke session env",
          config = Some(EnvironmentConfig.Cloud())
        )
      )
      _ = println(s"agent=${agent.id} env=${env.id}")
      // run the session flow, then always clean up the agent + environment
      _ <- sessionFlow(agent.id, env.id).transformWith { result =>
        service
          .archiveAgent(agent.id)
          .flatMap(_ => service.deleteEnvironment(env.id))
          .transform(_ => result)
      }
    } yield ()

  private def sessionFlow(
    agentId: String,
    environmentId: String
  ): Future[Unit] =
    for {
      session <- service.createSession(
        AnthropicCreateSessionSettings(
          agentId = agentId,
          environmentId = environmentId,
          title = Some("smoke session")
        )
      )
      _ = println(s"create: id=${session.id} status=${session.status}")

      fetched <- service.getSession(session.id)
      _ = println(s"get: ${fetched.title}")

      listed <- service.listSessions(limit = Some(5))
      _ = println(s"list: count=${listed.data.size} nextPage=${listed.nextPage}")

      updated <- service.updateSession(
        session.id,
        AnthropicUpdateSessionSettings(title = Some("smoke session (updated)"))
      )
      _ = println(s"update: title=${updated.title}")

      events <- service.listSessionEvents(session.id, limit = Some(10))
      _ = println(s"events: ${events.data.size} (types=${events.data.map(_.`type`).distinct})")

      resources <- service.listSessionResources(session.id)
      _ = println(s"resources: ${resources.data.size}")

      _ <- service.archiveSession(session.id)
      deleted <- service.deleteSession(session.id)
      _ = println(s"archive + delete: ${deleted.`type`}")
    } yield ()
}
