package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.JsonSchema.JsonSchemaOrMap

case class JsonSchemaDef(
  name: String,
  strict: Boolean = false,
  structure: JsonSchemaOrMap // rename to jsonSchema
)

object JsonSchemaDef {
  def apply(
    name: String,
    strict: Boolean,
    @Deprecated
    structure: Map[String, Any]
  ): JsonSchemaDef = JsonSchemaDef(name, strict, Right(structure))

  def apply(
    name: String,
    strict: Boolean,
    structure: JsonSchema
  ): JsonSchemaDef = JsonSchemaDef(name, strict, Left(structure))
}
