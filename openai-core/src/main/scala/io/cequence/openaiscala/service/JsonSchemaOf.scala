package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.JsonSchema

/**
 * The JSON schema of a type, for APIs that need one per type - e.g. TypeSafe's `decide[T]`,
 * and the JSON helpers' inferred response schema ([[InferredJsonSchema]]). Derived from a case
 * class (see [[JsonSchemaReflectionHelper.jsonSchemaFor]]: runtime reflection on Scala 2, a
 * macro on Scala 3, the same schema on both), unless an instance of your own is in scope:
 *
 * {{{
 * implicit val triageSchema: JsonSchemaOf[Triage] = JsonSchemaOf.instance("Triage", mySchema)
 * }}}
 */
trait JsonSchemaOf[T] {
  def schema: JsonSchema

  /**
   * The schema's name - the type's simple name for a derived instance. A JSON helper names the
   * schema it infers from this instance after it (`response` when there is none).
   */
  def name: Option[String] = None
}

object JsonSchemaOf extends JsonSchemaOfDerivation {

  def apply[T](
    implicit schemaOf: JsonSchemaOf[T]
  ): JsonSchemaOf[T] = schemaOf

  /** An instance with this schema (evaluated once, when first needed). */
  def instance[T](jsonSchema: => JsonSchema): JsonSchemaOf[T] =
    new JsonSchemaOf[T] {
      override lazy val schema: JsonSchema = jsonSchema
    }

  /** An instance with this name and schema (the schema evaluated once, when first needed). */
  def instance[T](
    schemaName: String,
    jsonSchema: => JsonSchema
  ): JsonSchemaOf[T] =
    new JsonSchemaOf[T] {
      override lazy val schema: JsonSchema = jsonSchema
      override val name: Option[String] = Some(schemaName)
    }
}
