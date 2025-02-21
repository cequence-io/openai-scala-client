package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.NamedEnumValue

// Base trait for all JSON Schema elements
sealed trait JsonSchema {
  def `type`: JsonType
}

object JsonSchema {

  import java.lang.{String => JString}

  type JsonSchemaOrMap = Either[JsonSchema, Map[JString, Any]]

  case class Object(
    properties: Seq[(JString, JsonSchema)],
    required: Seq[JString] = Nil
  ) extends JsonSchema {
    override val `type` = JsonType.Object
  }

  def Object(
    properties: Map[JString, JsonSchema],
    required: Seq[JString] = Nil
  ): Object = Object(properties.toSeq, required)

  case class String(
    description: Option[JString] = None,
    `enum`: Seq[JString] = Nil
  ) extends JsonSchema {
    override val `type` = JsonType.String
  }
  case class Number(
    description: Option[JString] = None
  ) extends JsonSchema {
    override val `type` = JsonType.Number
  }
  case class Integer(
    description: Option[JString] = None
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
    items: JsonSchema
  ) extends JsonSchema {
    override val `type` = JsonType.Array
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
