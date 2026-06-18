package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentModelConfig,
  AgentTool,
  DeploymentInitialEvent,
  EnvironmentConfig
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicCreateDeploymentSettings,
  AnthropicCreateEnvironmentSettings
}
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Live check of deployment lifecycle + runs: create a deployment, pause/unpause it, list its
 * runs, and trigger a run, then archive everything.
 */
object AnthropicDeploymentRunsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      agent <- service.createAgent(
        AnthropicCreateAgentSettings(
          name = "openai-scala-client smoke run agent",
          model = AgentModelConfig(NonOpenAIModelId.claude_opus_4_6),
          system = Some("You are concise."),
          tools = Seq(AgentTool.Toolset())
        )
      )
      env <- service.createEnvironment(
        AnthropicCreateEnvironmentSettings(
          name = "openai-scala-client smoke run env",
          config = Some(EnvironmentConfig.Cloud())
        )
      )
      _ <- runsFlow(agent.id, env.id).transformWith { result =>
        service
          .archiveAgent(agent.id)
          .flatMap(_ => service.deleteEnvironment(env.id))
          .transform(_ => result)
      }
    } yield ()

  private def runsFlow(
    agentId: String,
    environmentId: String
  ): Future[Unit] =
    for {
      deployment <- service.createDeployment(
        AnthropicCreateDeploymentSettings(
          agentId = agentId,
          environmentId = environmentId,
          name = "openai-scala-client smoke runs deployment",
          initialEvents = Seq(DeploymentInitialEvent.UserMessage("Say hi."))
        )
      )
      _ = println(s"deployment=${deployment.id} status=${deployment.status}")

      paused <- service.pauseDeployment(deployment.id)
      _ = println(s"pause: status=${paused.status}")

      unpaused <- service.unpauseDeployment(deployment.id)
      _ = println(s"unpause: status=${unpaused.status}")

      before <- service.listDeploymentRuns(deployment.id)
      _ = println(s"runs before: ${before.data.size}")

      _ <- service.runDeployment(deployment.id).transform {
        case Success(r) =>
          println(s"run: id=${r.id} status=${r.status} sessionId=${r.sessionId}"); Success(())
        case Failure(e) =>
          println(s"run failed (may require an invocable model): ${e.getMessage}"); Success(())
      }

      after <- service.listDeploymentRuns(deployment.id)
      _ = println(
        s"runs after: ${after.data.size} (statuses=${after.data.flatMap(_.status).distinct})"
      )

      _ <- service.archiveDeployment(deployment.id)
      _ = println("archived deployment")
    } yield ()
}
