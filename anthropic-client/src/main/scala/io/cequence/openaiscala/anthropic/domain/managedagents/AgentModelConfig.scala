package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.openaiscala.anthropic.domain.settings.Speed

/**
 * Model configuration for a Managed Agent.
 *
 * Requests accept either a bare model-id string or an object `{id, speed}`; responses always
 * return the object form. Serialized as a bare string when [[speed]] is `None`, otherwise as
 * an object (see the JSON format).
 *
 * @param id
 *   Model identifier, e.g. `claude-fable-5`.
 * @param speed
 *   Inference speed (`standard` or `fast`). `fast` is only supported on some models.
 */
final case class AgentModelConfig(
  id: String,
  speed: Option[Speed] = None
)

object AgentModelConfig {

  /** Convenience: a bare model id with no explicit speed. */
  def apply(id: String): AgentModelConfig = AgentModelConfig(id, None)
}
