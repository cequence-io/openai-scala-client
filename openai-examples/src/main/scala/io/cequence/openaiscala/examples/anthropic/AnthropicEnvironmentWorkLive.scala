package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.EnvironmentConfig
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateEnvironmentSettings
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live check that a self-hosted environment can be created (and deleted) with a workspace API
 * key.
 *
 * The work-queue endpoints (`listWork`, `getWork`, `pollWork`, `acknowledgeWork`,
 * `recordWorkHeartbeat`, `stopWork`, `updateWork`, `getWorkQueueStats`) are part of the
 * self-hosted worker protocol and authenticate with the per-environment **environment key** (a
 * bearer token minted in Console), not the workspace `x-api-key` — calling them with an API
 * key returns 401 "Invalid bearer token". They are normally driven by the SDK/CLI worker; this
 * example therefore does not invoke them. Requires `ANTHROPIC_API_KEY` with the
 * `managed-agents-2026-04-01` beta.
 */
object AnthropicEnvironmentWorkLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      println("=== create self-hosted environment ===")
      val env = Await.result(
        service.createEnvironment(
          AnthropicCreateEnvironmentSettings(
            name = "openai-scala-client smoke self-hosted env",
            config = Some(EnvironmentConfig.SelfHosted)
          )
        ),
        2.minutes
      )
      println(s"id=${env.id} config=${env.config}")

      // Work-queue endpoints require the environment key (bearer), not the workspace API key,
      // so they are intentionally not called here. Uncomment with an environment-key-backed
      // service to drive them:
      //   service.getWorkQueueStats(env.id)
      //   service.listWork(env.id, limit = Some(5))

      println("\n=== delete ===")
      println(Await.result(service.deleteEnvironment(env.id), 1.minute).`type`)

      println("\nSelf-hosted environment create/delete smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
