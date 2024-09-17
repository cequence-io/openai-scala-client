package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.JsonSchema

case class JsonSchemaDef(
  name: String,
  strict: Boolean = false,
  structure: Either[JsonSchema, Map[String, Any]] // rename to jsonSchema
)

object JsonSchemaDef {
  def apply(
    name: String,
    strict: Boolean,
    structure: Map[String, Any]
  ): JsonSchemaDef = JsonSchemaDef(name, strict, Right(structure))

  def apply(
    name: String,
    strict: Boolean,
    structure: JsonSchema
  ): JsonSchemaDef = JsonSchemaDef(name, strict, Left(structure))
}
