package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.JsonSchema

/**
 * A Scala type as `JsonSchemaReflectionHelper.jsonSchemaFor` sees it - built by runtime
 * reflection on Scala 2 and by a macro on Scala 3, then turned into a [[JsonSchema]] by
 * [[JsonSchemaShape.toJsonSchema]], so both Scala versions derive the same schema.
 *
 * Internal - public only because the Scala 3 macro expands into code that builds it.
 */
sealed trait JsonSchemaShape

object JsonSchemaShape {

  case object IntegerShape extends JsonSchemaShape
  case object NumberShape extends JsonSchemaShape
  case object BooleanShape extends JsonSchemaShape
  case object StringShape extends JsonSchemaShape

  /** `java.util.Date` - a number or a string, as the caller asks. */
  case object DateShape extends JsonSchemaShape

  /**
   * A string with fixed values.
   *
   * @param descriptions
   *   value -> its description (`@JsonSchemaDescription` on a case object or enum case) -
   *   written into the field's description, since a JSON schema enum has no place for them
   */
  final case class EnumShape(
    values: Seq[String],
    descriptions: Map[String, String] = Map()
  ) extends JsonSchemaShape

  /** The inner type, its field not required. */
  final case class OptionShape(inner: JsonSchemaShape) extends JsonSchemaShape

  final case class ArrayShape(items: JsonSchemaShape) extends JsonSchemaShape

  final case class ObjectShape(
    fields: Seq[FieldShape],
    description: Option[String] = None
  ) extends JsonSchemaShape

  final case class FieldShape(
    name: String,
    shape: JsonSchemaShape,
    description: Option[String] = None,
    minimum: Option[Double] = None,
    maximum: Option[Double] = None
  )

  /** An enum of these values, each with its description if it has one - sorted by value. */
  def sortedEnumShape(options: Seq[(String, Option[String])]): EnumShape = {
    val sorted = options.sortBy(_._1)
    EnumShape(
      sorted.map(_._1),
      sorted.collect { case (value, Some(description)) => value -> description }.toMap
    )
  }

  /** An enum of these values, each with its description if it has one - in this order. */
  def enumShape(options: Seq[(String, Option[String])]): EnumShape =
    EnumShape(
      options.map(_._1),
      options.collect { case (value, Some(description)) => value -> description }.toMap
    )

  /** The names of an `Enumeration`'s values, in declaration order. */
  def enumerationValues(enumeration: Enumeration): Seq[String] =
    enumeration.values.toSeq.map(_.toString)

  /** The names of a Java enum's constants, in declaration order. */
  def javaEnumValues(enumClass: Class[_]): Seq[String] =
    enumClass.getEnumConstants.toSeq.map(_.asInstanceOf[java.lang.Enum[_]].name)

  /**
   * @param dateAsNumber
   *   a `java.util.Date` as a number (epoch millis - Play JSON's default format) rather than a
   *   string
   * @param explicitTypes
   *   schemas by field name, used instead of the derived ones (at any depth)
   */
  def toJsonSchema(
    shape: JsonSchemaShape,
    dateAsNumber: Boolean = false,
    explicitTypes: Map[String, JsonSchema] = Map()
  ): JsonSchema = {

    def convert(
      shape: JsonSchemaShape,
      description: Option[String],
      minimum: Option[Double],
      maximum: Option[Double]
    ): JsonSchema =
      shape match {
        case IntegerShape =>
          JsonSchema.Integer(
            description,
            minimum = minimum.map(value => math.ceil(value).toLong),
            maximum = maximum.map(value => math.floor(value).toLong)
          )

        case NumberShape =>
          JsonSchema.Number(description, minimum, maximum)

        case BooleanShape =>
          JsonSchema.Boolean(description)

        case StringShape =>
          JsonSchema.String(description)

        case DateShape =>
          if (dateAsNumber) JsonSchema.Number(description) else JsonSchema.String(description)

        case EnumShape(values, descriptions) =>
          JsonSchema.String(withValueDescriptions(description, values, descriptions), values)

        case OptionShape(inner) =>
          convert(inner, description, minimum, maximum)

        // a multi-select: the values' descriptions go with the field's own
        case ArrayShape(EnumShape(values, descriptions)) if descriptions.nonEmpty =>
          JsonSchema.Array(
            JsonSchema.String(None, values),
            withValueDescriptions(description, values, descriptions)
          )

        case ArrayShape(items) =>
          JsonSchema.Array(convert(items, None, minimum, maximum), description)

        case ObjectShape(fields, classDescription) =>
          val properties = fields.map { field =>
            field.name -> explicitTypes.getOrElse(
              field.name,
              convert(field.shape, field.description, field.minimum, field.maximum)
            )
          }

          JsonSchema.Object(
            properties,
            required = fields.collect {
              case field if !field.shape.isInstanceOf[OptionShape] => field.name
            },
            description = description.orElse(classDescription)
          )
      }

    convert(shape, None, None, None)
  }

  // the field's description followed by a "- value: description" line per described value
  private def withValueDescriptions(
    description: Option[String],
    values: Seq[String],
    descriptions: Map[String, String]
  ): Option[String] = {
    val lines =
      values.flatMap(value => descriptions.get(value).map(text => s"- $value: $text"))
    if (lines.isEmpty) description
    else Some((description.toSeq ++ lines).mkString("\n"))
  }

  /**
   * An enum field's description (a multi-select array's) split into its own text and the
   * values' descriptions - the "- value: description" lines [[toJsonSchema]] appends for
   * described values
   *   - so a decision model can be asked with each value's description where it belongs (a
   *     choice's per-option criteria, a multi-select option's own question). The value lines
   *     start at the first line naming one of `values`; a line after it that names none
   *     continues the previous value's description.
   */
  def splitValueDescriptions(
    description: String,
    values: Seq[String]
  ): (String, Map[String, String]) = {
    val prefixes = values.map(value => value -> s"- $value: ")
    def valueOf(line: String) = prefixes.find { case (_, prefix) => line.startsWith(prefix) }

    val lines = description.split("\n", -1).toSeq
    val start = lines.indexWhere(valueOf(_).isDefined)

    if (start < 0) (description, Map())
    else {
      val described =
        lines.drop(start).foldLeft(Vector.empty[(String, String)]) { case (done, line) =>
          (valueOf(line), done) match {
            case (Some((value, prefix)), _)      => done :+ (value -> line.drop(prefix.length))
            case (None, init :+ ((value, text))) => init :+ (value -> s"$text\n$line")
            case (None, _)                       => done // not reached: `start` names a value
          }
        }
      (lines.take(start).mkString("\n"), described.toMap)
    }
  }
}
