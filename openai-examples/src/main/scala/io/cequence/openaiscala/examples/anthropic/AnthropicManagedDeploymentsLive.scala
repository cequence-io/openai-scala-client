package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
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
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Deployments API: create an agent + environment, then create ->
 * get -> list -> update -> archive a deployment (no schedule, so it does not run), and clean
 * up the agent and environment. Requires `ANTHROPIC_API_KEY` with the
 * `managed-agents-2026-04-01` beta.
 */
object AnthropicManagedDeploymentsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      val agent = Await.result(
        service.createAgent(
          AnthropicCreateAgentSettings(
            name = "openai-scala-client smoke deploy agent",
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
            name = "openai-scala-client smoke deploy env",
            config = Some(EnvironmentConfig.Cloud())
          )
        ),
        2.minutes
      )
      println(s"agent=${agent.id} env=${env.id}")

      try {
        println("\n=== create deployment (no schedule → manual) ===")
        val deployment = Await.result(
          service.createDeployment(
            AnthropicCreateDeploymentSettings(
              agentId = agent.id,
              environmentId = env.id,
              name = "openai-scala-client smoke deployment",
              initialEvents = Seq(DeploymentInitialEvent.UserMessage("Say hello.")),
              description = Some("smoke-test deployment")
            )
          ),
          2.minutes
        )
        println(s"id=${deployment.id} status=${deployment.status}")

        println("\n=== get ===")
        println(Await.result(service.getDeployment(deployment.id), 1.minute).name)

        println("\n=== list ===")
        val listed = Await.result(service.listDeployments(limit = Some(5)), 1.minute)
        println(s"count=${listed.data.size} nextPage=${listed.nextPage}")

        println("\n=== update description ===")
        val updated = Await.result(
          service.updateDeployment(
            deployment.id,
            AnthropicUpdateDeploymentSettings(description =
              Some("updated smoke-test deployment")
            )
          ),
          1.minute
        )
        println(s"description=${updated.description}")

        println("\n=== archive ===")
        val archived = Await.result(service.archiveDeployment(deployment.id), 1.minute)
        println(s"status=${archived.status} archivedAt=${archived.archivedAt}")
      } finally {
        Await.result(service.archiveAgent(agent.id), 1.minute)
        Await.result(service.deleteEnvironment(env.id), 1.minute)
      }

      println("\nDeployments smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
