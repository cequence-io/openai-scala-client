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
  AnthropicCreateEnvironmentSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

/**
 * Live check of deployment lifecycle + runs: create a deployment, pause/unpause it, list its
 * runs, and trigger a run, then archive everything. Requires `ANTHROPIC_API_KEY` with the
 * `managed-agents-2026-04-01` beta.
 */
object AnthropicDeploymentRunsLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      val agent = Await.result(
        service.createAgent(
          AnthropicCreateAgentSettings(
            name = "openai-scala-client smoke run agent",
            model = AgentModelConfig(NonOpenAIModelId.claude_opus_4_6),
            system = Some("You are concise."),
            tools = Seq(AgentTool.Toolset())
          )
        ),
        2.minutes
      )
      val env = Await.result(
        service.createEnvironment(
          AnthropicCreateEnvironmentSettings(
            name = "openai-scala-client smoke run env",
            config = Some(EnvironmentConfig.Cloud())
          )
        ),
        2.minutes
      )

      try {
        val deployment = Await.result(
          service.createDeployment(
            AnthropicCreateDeploymentSettings(
              agentId = agent.id,
              environmentId = env.id,
              name = "openai-scala-client smoke runs deployment",
              initialEvents = Seq(DeploymentInitialEvent.UserMessage("Say hi."))
            )
          ),
          2.minutes
        )
        println(s"deployment=${deployment.id} status=${deployment.status}")

        println("\n=== pause ===")
        println(
          s"status=${Await.result(service.pauseDeployment(deployment.id), 1.minute).status}"
        )

        println("\n=== unpause ===")
        println(
          s"status=${Await.result(service.unpauseDeployment(deployment.id), 1.minute).status}"
        )

        println("\n=== list runs (before) ===")
        println(
          s"runs=${Await.result(service.listDeploymentRuns(deployment.id), 1.minute).data.size}"
        )

        println("\n=== run ===")
        Try(Await.result(service.runDeployment(deployment.id), 2.minutes)) match {
          case Success(run) =>
            println(s"run id=${run.id} status=${run.status} sessionId=${run.sessionId}")
          case Failure(e) =>
            println(s"run failed (may require an invocable/ungated model): ${e.getMessage}")
        }

        println("\n=== list runs (after) ===")
        val runs = Await.result(service.listDeploymentRuns(deployment.id), 1.minute)
        println(s"runs=${runs.data.size} (statuses=${runs.data.flatMap(_.status).distinct})")

        println("\n=== archive deployment ===")
        Await.result(service.archiveDeployment(deployment.id), 1.minute)
        println("archived")
      } finally {
        Await.result(service.archiveAgent(agent.id), 1.minute)
        Await.result(service.deleteEnvironment(env.id), 1.minute)
      }

      println("\nDeployment runs smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
