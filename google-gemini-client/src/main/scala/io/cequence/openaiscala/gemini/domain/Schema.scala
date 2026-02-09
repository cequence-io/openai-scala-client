package io.cequence.openaiscala.gemini.domain

import io.cequence.wsclient.domain.EnumValue

/**
 * The Schema object allows the definition of input and output data types. These types can be
 * objects, but also primitives and arrays. Represents a select subset of an OpenAPI 3.0 schema
 * object.
 *
 * @param `type`
 *   Required. Data type.
 * @param format
 *   Optional. The format of the data. This is used only for primitive datatypes. Supported
 *   formats: for NUMBER type: float, double for INTEGER type: int32, int64 for STRING type:
 *   enum
 * @param description
 *   Optional. A brief description of the parameter. This could contain examples of use.
 *   Parameter description may be formatted as Markdown.
 * @param nullable
 *   Optional. Indicates if the value may be null.
 * @param enum
 *   Optional. Possible values of the element of Type.STRING with enum format. For example we
 *   can define an Enum Direction as : {type:STRING, format:enum, enum:["EAST", NORTH",
 *   "SOUTH", "WEST"]}
 * @param maxItems
 *   Optional. Maximum number of the elements for Type.ARRAY.
 * @param minItems
 *   Optional. Minimum number of the elements for Type.ARRAY.
 * @param properties
 *   Optional. Properties of Type.OBJECT. An object containing a list of "key": value pairs.
 * @param required
 *   Optional. Required properties of Type.OBJECT.
 * @param propertyOrdering
 *   Optional. The order of the properties. Not a standard field in open api spec. Used to
 *   determine the order of the properties in the response.
 * @param items
 *   Optional. Schema of the elements of Type.ARRAY.
 */
case class Schema(
  `type`: SchemaType,
  format: Option[String] = None,
  description: Option[String] = None,
  nullable: Option[Boolean] = None,
  `enum`: Option[Seq[String]] = None,
  maxItems: Option[String] = None,
  minItems: Option[String] = None,
  properties: Option[Map[String, Schema]] = None,
  required: Option[Seq[String]] = None,
  propertyOrdering: Option[Seq[String]] = None,
  items: Option[Schema] = None
)

sealed trait SchemaType extends EnumValue

object SchemaType {
  case object TYPE_UNSPECIFIED extends SchemaType
  case object STRING extends SchemaType
  case object NUMBER extends SchemaType
  case object INTEGER extends SchemaType
  case object BOOLEAN extends SchemaType
  case object ARRAY extends SchemaType
  case object OBJECT extends SchemaType

  def values: Seq[SchemaType] = Seq(
    TYPE_UNSPECIFIED,
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT
  )
}
