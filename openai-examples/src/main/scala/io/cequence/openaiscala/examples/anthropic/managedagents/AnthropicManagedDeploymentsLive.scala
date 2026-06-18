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
  AnthropicCreateEnvironmentSettings,
  AnthropicUpdateDeploymentSettings
}
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.Future

/**
 * Live end-to-end check of the Deployments API: create an agent + environment, then create ->
 * get -> list -> update -> archive a deployment (no schedule, so it does not run), and clean
 * up the agent and environment.
 */
object AnthropicManagedDeploymentsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      agent <- service.createAgent(
        AnthropicCreateAgentSettings(
          name = "openai-scala-client smoke deploy agent",
          model = AgentModelConfig(NonOpenAIModelId.claude_opus_4_6),
          system = Some("You are a concise assistant."),
          tools = Seq(AgentTool.Toolset())
        )
      )
      env <- service.createEnvironment(
        AnthropicCreateEnvironmentSettings(
          name = "openai-scala-client smoke deploy env",
          config = Some(EnvironmentConfig.Cloud())
        )
      )
      _ = println(s"agent=${agent.id} env=${env.id}")
      _ <- deploymentFlow(agent.id, env.id).transformWith { result =>
        service
          .archiveAgent(agent.id)
          .flatMap(_ => service.deleteEnvironment(env.id))
          .transform(_ => result)
      }
    } yield ()

  private def deploymentFlow(
    agentId: String,
    environmentId: String
  ): Future[Unit] =
    for {
      deployment <- service.createDeployment(
        AnthropicCreateDeploymentSettings(
          agentId = agentId,
          environmentId = environmentId,
          name = "openai-scala-client smoke deployment",
          initialEvents = Seq(DeploymentInitialEvent.UserMessage("Say hello.")),
          description = Some("smoke-test deployment")
        )
      )
      _ = println(s"create: id=${deployment.id} status=${deployment.status}")

      fetched <- service.getDeployment(deployment.id)
      _ = println(s"get: ${fetched.name}")

      listed <- service.listDeployments(limit = Some(5))
      _ = println(s"list: count=${listed.data.size} nextPage=${listed.nextPage}")

      updated <- service.updateDeployment(
        deployment.id,
        AnthropicUpdateDeploymentSettings(description = Some("updated smoke-test deployment"))
      )
      _ = println(s"update: description=${updated.description}")

      archived <- service.archiveDeployment(deployment.id)
      _ = println(s"archive: status=${archived.status} archivedAt=${archived.archivedAt}")
    } yield ()
}
