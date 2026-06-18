package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  EnvironmentConfig,
  EnvironmentScope
}

/**
 * Parameters for updating an environment (`POST /v1/environments/{id}`). `None` means "omit
 * (preserve existing)". `metadata` is a patch: a value of `None` for a key deletes it. Changes
 * apply only to new containers; running sessions keep their original config.
 */
final case class AnthropicUpdateEnvironmentSettings(
  config: Option[EnvironmentConfig] = None,
  name: Option[String] = None,
  description: Option[String] = None,
  metadata: Option[Map[String, Option[String]]] = None,
  scope: Option[EnvironmentScope] = None
)
