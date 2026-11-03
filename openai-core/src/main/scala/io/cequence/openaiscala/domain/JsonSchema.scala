package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.NamedEnumValue

/**
 * A JSON schema: a schema of one JSON type ([[JsonSchema.Typed]] - object, array, string,
 * number, integer, boolean, null) or a union of schemas ([[JsonSchema.AnyOf]], JSON Schema's
 * `anyOf`; an `Option` field derives as one - [[JsonSchema.Nullable]]).
 */
sealed trait JsonSchema

object JsonSchema {

  import java.lang.{String => JString}

  /** A schema of one JSON type - every kind but [[AnyOf]]. */
  sealed trait Typed extends JsonSchema {
    def `type`: JsonType
  }

  @Deprecated
  type JsonSchemaOrMap = Either[JsonSchema, Map[JString, Any]]

  case class Object(
    properties: Seq[(JString, JsonSchema)],
    required: Seq[JString] = Nil,
    additionalProperties: Option[scala.Boolean] = None,
    description: Option[JString] = None
  ) extends Typed {
    override val `type` = JsonType.Object
  }

  def ObjectAsMap(
    properties: Map[JString, JsonSchema],
    required: Seq[JString] = Nil,
    additionalProperties: Option[scala.Boolean] = None
  ): Object = Object(properties.toSeq, required, additionalProperties)

  case class String(
    description: Option[JString] = None,
    `enum`: Seq[JString] = Nil
  ) extends Typed {
    override val `type` = JsonType.String
  }

  /**
   * `minimum` / `maximum` / `enum` are standard JSON-schema keywords whose support across the
   * providers is UNEVEN (live-verified 2026-09-17):
   *
   *   - OpenAI: honours both in STRICT mode (`JsonSchemaDef.strict = true`) on gpt-5.4-mini
   *     and gpt-6-astra - a `minimum` 30 / `maximum` 40 rating came back in range; with
   *     `strict = false` the bounds are accepted but IGNORED (the same prompt answered 4)
   *   - Anthropic: rejects `minimum` / `maximum` with a 400 ("For 'number' type, properties
   *     maximum, minimum are not supported"), so the adapter drops them with a warning; `enum`
   *     is honoured
   *   - Gemini / Vertex AI: the adapters read only the description, so all three are dropped
   *
   * So `enum` travels everywhere, while `minimum` / `maximum` are OpenAI-strict-only.
   */
  case class Number(
    description: Option[JString] = None,
    minimum: Option[Double] = None,
    maximum: Option[Double] = None,
    `enum`: Seq[Double] = Nil
  ) extends Typed {
    override val `type` = JsonType.Number
  }

  /**
   * `minimum` / `maximum` / `enum` are standard JSON-schema keywords whose support across the
   * providers is UNEVEN (live-verified 2026-09-17):
   *
   *   - OpenAI: honours both in STRICT mode (`JsonSchemaDef.strict = true`) on gpt-5.4-mini
   *     and gpt-6-astra - a `minimum` 30 / `maximum` 40 rating came back in range; with
   *     `strict = false` the bounds are accepted but IGNORED (the same prompt answered 4)
   *   - Anthropic: rejects `minimum` / `maximum` with a 400 ("For 'integer' type, properties
   *     maximum, minimum are not supported"), so the adapter drops them with a warning; `enum`
   *     is honoured
   *   - Gemini / Vertex AI: the adapters read only the description, so all three are dropped
   *
   * So `enum` travels everywhere, while `minimum` / `maximum` are OpenAI-strict-only.
   */
  case class Integer(
    description: Option[JString] = None,
    minimum: Option[Long] = None,
    maximum: Option[Long] = None,
    `enum`: Seq[Long] = Nil
  ) extends Typed {
    override val `type` = JsonType.Integer
  }
  case class Boolean(
    description: Option[JString] = None
  ) extends Typed {
    override val `type` = JsonType.Boolean
  }
  case class Null() extends Typed {
    override val `type` = JsonType.Null
  }
  case class Array(
    items: JsonSchema,
    description: Option[JString] = None
  ) extends Typed {
    override val `type` = JsonType.Array
  }

  /**
   * A value valid against at least one of `schemas` - JSON Schema's `anyOf`, written as
   * `{"anyOf": [...], "description": ...}`. Its most common form is a nullable value, `anyOf`
   * a schema and [[Null]] - how an `Option` field derives ([[Nullable]] builds and matches
   * it).
   *
   * Provider support (live 2026-11-03, strict `json_schema`):
   *   - nullable (`[<schema>, null]`): OpenAI, Anthropic, Gemini's OpenAI endpoint, Mistral,
   *     Groq, Fireworks, Together, Cerebras, xAI and Meta all took it and answered null or a
   *     value; the other spellings fail - `"type": [<t>, "null"]` next to an `enum` is a 400
   *     on Anthropic and, with the `enum` lacking `null`, forces a value on OpenAI, Groq,
   *     Fireworks, Cerebras and xAI; OpenAPI's `nullable: true` (not JSON Schema) is a 400 on
   *     Anthropic and Cerebras, and is ignored by OpenAI, Fireworks, Together, xAI and Meta
   *   - the native Gemini and Vertex AI adapters send a nullable union (one schema and
   *     [[Null]] - an `Option` field) as that schema with `nullable: true`, since Gemini
   *     answered a `{"type": "NULL"}` branch with "" or "None", never null; any other union as
   *     their own `anyOf` (`type` TYPE_UNSPECIFIED, `nullable: true` when it has a [[Null]]
   *     branch) - Gemini answered a union of objects and string | integer right
   *   - OpenAI's strict mode refuses `anyOf` at the root
   *   - a decision model (`TypeSafeServiceFactory.asOpenAI`) takes only the nullable form, as
   *     the schema's own type (it always answers)
   *
   * Read from `anyOf` as written, and from `"type": [<t>, ...]` (one branch per type, `null`
   * dropped from an `enum`).
   */
  case class AnyOf(
    schemas: Seq[JsonSchema],
    description: Option[JString] = None
  ) extends JsonSchema

  /**
   * A schema or null: `AnyOf(Seq(<schema>, Null()))`, its description on the union - how an
   * `Option` field derives (`JsonSchemaReflectionHelper.jsonSchemaFor`; OpenAI's strict mode
   * wants every field required, so an optional field is a required one that may be null, which
   * Play JSON reads as `None`).
   */
  object Nullable {

    /**
     * `inner` or null, its description moved to the union. A schema that already takes null is
     * returned as it is ([[Null]], a nullable union), so it never nests; another union gets a
     * [[Null]] branch.
     */
    def apply(inner: JsonSchema): JsonSchema =
      inner match {
        case _: Null                                                    => inner
        case union: AnyOf if union.schemas.exists(_.isInstanceOf[Null]) => inner
        case union: AnyOf => union.copy(schemas = union.schemas :+ Null())
        case other =>
          AnyOf(Seq(withDescription(other, None), Null()), descriptionOf(other))
      }

    /**
     * The non-null schema of a union of one schema and [[Null]] (either order), with the
     * union's description unless it has its own.
     */
    def unapply(schema: JsonSchema): Option[JsonSchema] =
      schema match {
        case AnyOf(schemas, description) =>
          schemas.partition(_.isInstanceOf[Null]) match {
            case (Seq(_), Seq(inner)) =>
              Some(
                if (descriptionOf(inner).isDefined)
                  inner
                else
                  withDescription(inner, description)
              )
            case _ => None
          }
        case _ => None
      }
  }

  /** The description of a schema of any kind. */
  def descriptionOf(schema: JsonSchema): Option[JString] =
    schema match {
      case s: String  => s.description
      case s: Number  => s.description
      case s: Integer => s.description
      case s: Boolean => s.description
      case _: Null    => None
      case s: Object  => s.description
      case s: Array   => s.description
      case s: AnyOf   => s.description
    }

  /** The schema with this description ([[Null]] has none). */
  def withDescription(
    schema: JsonSchema,
    description: Option[JString]
  ): JsonSchema =
    schema match {
      case s: String  => s.copy(description = description)
      case s: Number  => s.copy(description = description)
      case s: Integer => s.copy(description = description)
      case s: Boolean => s.copy(description = description)
      case s: Null    => s
      case s: Object  => s.copy(description = description)
      case s: Array   => s.copy(description = description)
      case s: AnyOf   => s.copy(description = description)
    }

  /**
   * The schema with each required nullable property (`Nullable(inner)` - how an `Option` field
   * derives) as an optional property of its inner schema, the form derivations made before
   * 1.6.1 (`nullableOptions = false`): for providers' non-strict modes, where some hosts
   * mishandle a nullable union in tool parameters. Applied at every depth - object properties,
   * array items, union branches.
   */
  def optionalFields(schema: JsonSchema): JsonSchema =
    schema match {
      case obj: Object =>
        val converted = obj.properties.map { case (name, prop) =>
          prop match {
            case Nullable(inner) if obj.required.contains(name) =>
              (name, optionalFields(inner), true)

            case _ =>
              (name, optionalFields(prop), false)
          }
        }
        val dropped = converted.collect { case (name, _, true) => name }.toSet

        obj.copy(
          properties = converted.map { case (name, prop, _) => name -> prop },
          required = obj.required.filterNot(dropped.contains)
        )

      case arr: Array =>
        arr.copy(items = optionalFields(arr.items))

      case union: AnyOf =>
        union.copy(schemas = union.schemas.map(optionalFields))

      case other =>
        other
    }

  /**
   * Walks the schema and sets `additionalProperties = Some(false)` on every nested [[Object]].
   * Used to satisfy OpenAI-style strict structured-output guarantees and providers that
   * require closed objects.
   *
   * @param overrideExisting
   *   when `false` (default), only sets `false` on objects that don't already specify
   *   `additionalProperties` (user values are preserved); when `true`, overrides existing
   *   values - use this for OpenAI strict mode, which requires `additionalProperties: false`
   *   on every object regardless of what the caller put there.
   */
  def setAdditionalPropertiesToFalse(
    schema: JsonSchema,
    overrideExisting: scala.Boolean = false
  ): JsonSchema =
    schema match {
      case obj: Object =>
        val newAdditional =
          if (overrideExisting) Some(false)
          else obj.additionalProperties.orElse(Some(false))
        obj.copy(
          properties = obj.properties.map { case (key, value) =>
            key -> setAdditionalPropertiesToFalse(value, overrideExisting)
          },
          additionalProperties = newAdditional
        )
      case arr: Array =>
        arr.copy(items = setAdditionalPropertiesToFalse(arr.items, overrideExisting))
      case union: AnyOf =>
        union.copy(schemas =
          union.schemas.map(setAdditionalPropertiesToFalse(_, overrideExisting))
        )
      case other => other
    }
}

object JsonType {
  case object Object extends JsonType("object")
  case object String extends JsonType("string")
  case object Number extends JsonType("number")
  case object Integer extends JsonType("integer")
  case object Boolean extends JsonType("boolean")
  case object Null extends JsonType("null")
  case object Array extends JsonType("array")
}

sealed abstract class JsonType(value: String) extends NamedEnumValue(value)
