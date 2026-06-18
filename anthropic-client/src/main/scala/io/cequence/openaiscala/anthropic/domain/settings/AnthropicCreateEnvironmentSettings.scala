package io.cequence.openaiscala.anthropic.domain.settings

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  EnvironmentConfig,
  EnvironmentScope
}

/**
 * Parameters for creating an environment (`POST /v1/environments`).
 *
 * @param name
 *   Human-readable name (required).
 * @param config
 *   Cloud or self-hosted configuration; defaults to a cloud environment if omitted.
 * @param scope
 *   Visibility scope (self-hosted only).
 */
final case class AnthropicCreateEnvironmentSettings(
  name: String,
  config: Option[EnvironmentConfig] = None,
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  scope: Option[EnvironmentScope] = None
)
