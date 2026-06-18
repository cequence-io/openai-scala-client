package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.managedagents.EnvironmentConfig
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateEnvironmentSettings

import scala.concurrent.Future

/**
 * Live check that a self-hosted environment can be created (and deleted) with a workspace API
 * key.
 *
 * The work-queue endpoints (`listWork`, `getWork`, `pollWork`, `acknowledgeWork`,
 * `recordWorkHeartbeat`, `stopWork`, `updateWork`, `getWorkQueueStats`) are part of the
 * self-hosted worker protocol and authenticate with the per-environment **environment key** (a
 * bearer token minted in Console), not the workspace `x-api-key` — calling them with an API
 * key returns 401 "Invalid bearer token". They are normally driven by the SDK/CLI worker; this
 * example therefore does not invoke them.
 */
object AnthropicEnvironmentWorkLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      env <- service.createEnvironment(
        AnthropicCreateEnvironmentSettings(
          name = "openai-scala-client smoke self-hosted env",
          config = Some(EnvironmentConfig.SelfHosted)
        )
      )
      _ = println(s"created self-hosted env: id=${env.id} config=${env.config}")

      // Work-queue endpoints require the environment key (bearer), not the workspace API key,
      // so they are intentionally not called here. With an environment-key-backed service:
      //   service.getWorkQueueStats(env.id)
      //   service.listWork(env.id, limit = Some(5))

      deleted <- service.deleteEnvironment(env.id)
      _ = println(s"delete: ${deleted.`type`}")
    } yield ()
}
