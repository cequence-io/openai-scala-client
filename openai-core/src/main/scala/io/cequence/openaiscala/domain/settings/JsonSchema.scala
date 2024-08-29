package io.cequence.openaiscala.domain.settings

case class JsonSchema(
  name: String,
  strict: Boolean = false,
  // TODO: introduce a proper json schema type / case classes
  structure: Map[String, Any]
)