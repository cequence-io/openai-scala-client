package io.cequence.openaiscala.vertexai.domain

import io.cequence.wsclient.domain.EnumValue

/**
 * The Schema object allows the definition of input and output data types for function
 * parameters.
 *
 * @param `type`
 *   Required. Data type.
 * @param format
 *   Optional. The format of the data.
 * @param description
 *   Optional. A brief description of the parameter.
 * @param nullable
 *   Optional. Indicates if the value may be null.
 * @param enum
 *   Optional. Possible values of the element of Type.STRING with enum format.
 * @param properties
 *   Optional. Properties of Type.OBJECT.
 * @param required
 *   Optional. Required properties of Type.OBJECT.
 * @param items
 *   Optional. Schema of the elements of Type.ARRAY.
 */
case class Schema(
  `type`: SchemaType,
  format: Option[String] = None,
  description: Option[String] = None,
  nullable: Option[Boolean] = None,
  `enum`: Option[Seq[String]] = None,
  properties: Option[Map[String, Schema]] = None,
  required: Option[Seq[String]] = None,
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
