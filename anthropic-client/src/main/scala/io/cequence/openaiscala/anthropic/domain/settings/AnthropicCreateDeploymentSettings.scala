package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  DeploymentInitialEvent,
  Schedule,
  SessionResource
}

/**
 * Parameters for creating a deployment (`POST /v1/deployments`).
 *
 * @param agentId
 *   The agent to deploy (required).
 * @param environmentId
 *   The environment to run in (required).
 * @param name
 *   Human-readable name (required).
 * @param initialEvents
 *   Events seeded into each run (1-50, required).
 * @param agentVersion
 *   Pin a specific agent version; latest if omitted.
 * @param schedule
 *   Optional cron schedule; without it the deployment is triggered manually.
 */
final case class AnthropicCreateDeploymentSettings(
  agentId: String,
  environmentId: String,
  name: String,
  initialEvents: Seq[DeploymentInitialEvent],
  agentVersion: Option[Int] = None,
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  resources: Seq[SessionResource] = Nil,
  schedule: Option[Schedule] = None,
  vaultIds: Seq[String] = Nil
)
