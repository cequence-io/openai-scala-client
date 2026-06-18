package io.cequence.openaiscala.anthropic.domain.settings

/** Parameters for creating a memory store (`POST /v1/memory_stores`). */
final case class AnthropicCreateMemoryStoreSettings(
  name: String,
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

/**
 * Parameters for updating a memory store (`POST /v1/memory_stores/{id}`). `None` means "omit
 * (preserve)". `metadata` is a patch: a value of `None` for a key deletes it.
 */
final case class AnthropicUpdateMemoryStoreSettings(
  name: Option[String] = None,
  description: Option[String] = None,
  metadata: Option[Map[String, Option[String]]] = None
)
