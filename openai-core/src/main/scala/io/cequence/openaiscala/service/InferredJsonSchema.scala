package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import org.slf4j.LoggerFactory
import play.api.libs.json.JsValue

import scala.util.{Failure, Success, Try}

/**
 * The response schema a JSON helper (`createChatCompletionWithJSON[T]`,
 * `createChatCompletionWithJSONFullResponse[T]`, `createChatCompletionBatchWithJSON[T]`)
 * infers for `T` when the settings carry no `jsonSchema` - an explicit one always wins:
 *   - `T`'s [[JsonSchemaOf]] - an instance of your own in scope, or the one derived from a
 *     case class with its `@JsonSchemaDescription` / `@JsonSchemaRange` annotations - as a
 *     `JsonSchemaDef` named after the type, `strict` only when OpenAI's strict mode takes the
 *     schema (every field required, no open object): an `Option` field or a `Map` makes it
 *     `strict = false`;
 *   - nothing for a JSON value (`JsValue`, `JsObject`, ...), a type whose schema is not an
 *     object with fields (an array, a string, a map, an enum), a type that does not derive (an
 *     `Either`, a sealed hierarchy of case classes, a tuple) and an abstract `T` - the call
 *     then parses JSON without a schema, as before.
 *
 * Generic code that calls a JSON helper passes the inference on with a context bound (`def
 * ask[T: Format: InferredJsonSchema]`); without one an abstract `T` infers nothing. To keep a
 * derivable `T` schema-less, put an instance of your own in scope:
 *
 * {{{
 * implicit val noSchema: InferredJsonSchema[Answer] = InferredJsonSchema.none
 * }}}
 */
trait InferredJsonSchema[T] {

  /** The schema definition to ask with, if any. */
  def jsonSchemaDef: Option[JsonSchemaDef]
}

object InferredJsonSchema extends InferredJsonSchemaDerivation {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName.stripSuffix("$"))

  /** The name of a schema whose type has none. */
  val DefaultName = "response"

  def apply[T](
    implicit inferred: InferredJsonSchema[T]
  ): InferredJsonSchema[T] = inferred

  private object NoSchema extends InferredJsonSchema[Any] {
    override val jsonSchemaDef: Option[JsonSchemaDef] = None
  }

  /** No schema - the JSON helper parses the reply without one. */
  def none[T]: InferredJsonSchema[T] = NoSchema.asInstanceOf[InferredJsonSchema[T]]

  /** This definition, as it is. */
  def of[T](definition: JsonSchemaDef): InferredJsonSchema[T] =
    new InferredJsonSchema[T] {
      override val jsonSchemaDef: Option[JsonSchemaDef] = Some(definition)
    }

  /**
   * The definition of a [[JsonSchemaOf]]'s schema - named after the instance (else
   * `fallbackName`), none when the schema is not an object with fields or cannot be built.
   */
  def fromSchemaOf[T](
    schemaOf: JsonSchemaOf[T],
    fallbackName: => String = DefaultName
  ): InferredJsonSchema[T] =
    new InferredJsonSchema[T] {
      override lazy val jsonSchemaDef: Option[JsonSchemaDef] =
        Try(schemaOf.schema) match {
          case Success(schema) =>
            definition(schemaOf.name.getOrElse(fallbackName), schema)

          // Scala 2 derives at runtime - a type it does not support is asked without a schema
          case Failure(e) =>
            logger.debug(s"No JSON schema inferred: ${e.getMessage}")
            None
        }
    }

  /** The definition of a derived shape (the Scala 3 inference). */
  def fromShape[T](
    name: String,
    shape: => JsonSchemaShape
  ): InferredJsonSchema[T] =
    new InferredJsonSchema[T] {
      override lazy val jsonSchemaDef: Option[JsonSchemaDef] =
        definition(name, JsonSchemaShape.toJsonSchema(shape))
    }

  // a JSON value has no schema to infer - ahead of the derivation, which would read a JsObject
  // as a case class with an internal map
  implicit def jsonValue[T <: JsValue]: InferredJsonSchema[T] = none[T]

  /** `raw` as a schema name, which OpenAI wants as `^[a-zA-Z0-9_-]{1,64}$`. */
  def schemaName(raw: String): String = {
    val cleaned = raw.replaceAll("[^a-zA-Z0-9_-]", "_").take(64)
    if (cleaned.isEmpty) DefaultName else cleaned
  }

  /**
   * Whether OpenAI's strict mode takes the schema: every object lists all its fields as
   * required and none is open (`additionalProperties: true`, how a `Map` derives).
   */
  def strictCompatible(schema: JsonSchema): Boolean =
    schema match {
      case obj: JsonSchema.Object =>
        !obj.additionalProperties.contains(true) &&
        obj.properties.map(_._1).toSet.subsetOf(obj.required.toSet) &&
        obj.properties.forall { case (_, fieldSchema) => strictCompatible(fieldSchema) }

      case array: JsonSchema.Array => strictCompatible(array.items)

      case _ => true
    }

  // OpenAI's structured outputs want an object at the root - any other schema (an array, a
  // string, an open map, an enum) is asked without one, as before
  private def definition(
    name: String,
    schema: JsonSchema
  ): Option[JsonSchemaDef] =
    schema match {
      case obj: JsonSchema.Object if obj.properties.nonEmpty =>
        Some(JsonSchemaDef(schemaName(name), strict = strictCompatible(schema), Left(schema)))

      case _ => None
    }
}
