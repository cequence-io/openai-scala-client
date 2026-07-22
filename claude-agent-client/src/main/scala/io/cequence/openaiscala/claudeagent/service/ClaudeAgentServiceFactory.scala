package io.cequence.openaiscala.claudeagent.service

import akka.stream.Materializer
import io.cequence.openaiscala.claudeagent.domain.ClaudeAgentSettings
import io.cequence.openaiscala.claudeagent.service.impl.{
  ClaudeAgentProcessTransport,
  ClaudeAgentServiceImpl
}

import scala.concurrent.ExecutionContext

object ClaudeAgentServiceFactory {

  /**
   * Spawns `claude` as a subprocess and returns a live session handle. Spawning is eager and
   * synchronous - the returned service's `events` stream will emit a `SystemInit` as its first
   * item once the CLI signals readiness; there is no separate async "wait for ready" step.
   */
  def startSession(
    settings: ClaudeAgentSettings = ClaudeAgentSettings()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): ClaudeAgentService =
    new ClaudeAgentServiceImpl(new ClaudeAgentProcessTransport(settings))
}
