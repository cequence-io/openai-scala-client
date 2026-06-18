package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  DeploymentInitialEvent,
  Schedule,
  SessionResource
}

/**
 * Parameters for updating a deployment (`POST /v1/deployments/{id}`). `None` means "omit
 * (preserve)". `initialEvents`/`resources`/`vaultIds` are full replacements when present.
 * `metadata` is a patch: a value of `None` for a key deletes it.
 */
final case class AnthropicUpdateDeploymentSettings(
  agentId: Option[String] = None,
  agentVersion: Option[Int] = None,
  environmentId: Option[String] = None,
  name: Option[String] = None,
  description: Option[String] = None,
  initialEvents: Option[Seq[DeploymentInitialEvent]] = None,
  metadata: Option[Map[String, Option[String]]] = None,
  resources: Option[Seq[SessionResource]] = None,
  schedule: Option[Schedule] = None,
  vaultIds: Option[Seq[String]] = None
)
