package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.NamedEnumValue

// Base trait for all JSON Schema elements
sealed trait JsonSchema {
  def `type`: JsonType
}

object JsonSchema {

  import java.lang.{String => JString}

  @Deprecated
  type JsonSchemaOrMap = Either[JsonSchema, Map[JString, Any]]

  case class Object(
    properties: Seq[(JString, JsonSchema)],
    required: Seq[JString] = Nil,
    additionalProperties: Option[scala.Boolean] = None,
    description: Option[JString] = None
  ) extends JsonSchema {
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
  ) extends JsonSchema {
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
  ) extends JsonSchema {
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
  ) extends JsonSchema {
    override val `type` = JsonType.Integer
  }
  case class Boolean(
    description: Option[JString] = None
  ) extends JsonSchema {
    override val `type` = JsonType.Boolean
  }
  case class Null() extends JsonSchema {
    override val `type` = JsonType.Null
  }
  case class Array(
    items: JsonSchema,
    description: Option[JString] = None
  ) extends JsonSchema {
    override val `type` = JsonType.Array
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
