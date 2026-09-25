package io.cequence.openaiscala.domain.settings

case class CreateModerationSettings(
  // omni-moderation-latest (the API default) or a dated omni-moderation snapshot; the text-moderation-* models are gone.
  model: Option[String] = None
)
