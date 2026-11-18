package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.Verbosity

/**
 * Represents the format of the response.
 */
sealed trait ResponseFormat {
  val `type`: String
}

object ResponseFormat {

  /**
   * Default response format. Used to generate text responses.
   */
  object Text extends ResponseFormat {
    val `type`: String = "text"
  }

  /**
   * JSON Schema response format. Used to generate structured JSON responses.
   *
   * @param schema
   *   The schema for the response format, described as a JSON Schema object.
   * @param description
   *   A description of what the response format is for, used by the model to determine how to
   *   respond in the format.
   * @param name
   *   The name of the response format. Must be a-z, A-Z, 0-9, or contain underscores and
   *   dashes, with a maximum length of 64.
   * @param strict
   *   Whether to enable strict schema adherence when generating the output.
   */
  // TODO: merge with JsonSchemaDef?
  case class JsonSchemaSpec(
    schema: JsonSchema,
    description: Option[String] = None,
    name: Option[String] = None,
    strict: Option[Boolean] = None
  ) extends ResponseFormat {
    val `type`: String = "json_schema"
  }

  /**
   * JSON object response format. An older method of generating JSON responses. Using
   * JsonSchema is recommended for models that support it.
   */
  object JsonObject extends ResponseFormat {
    val `type`: String = "json_object"
  }
}

/**
 * The `text` object of a Responses API request.
 *
 * @param format
 *   plain text (the default), a JSON schema or a JSON object
 * @param verbosity
 *   how many output tokens the model spends on its answer - `low`, `medium` or `high`
 *   (`text.verbosity`; the chat completions API's top-level `verbosity`)
 */
case class TextResponseConfig(
  format: ResponseFormat,
  verbosity: Option[Verbosity] = None
) {

  // Binary compatibility with 2.0.0 / 1.7.1 (before `verbosity`): their one-argument constructor
  // and `copy`. A hand-written `copy` suppresses the synthetic one, so both are written out;
  // `copy(format = f)` resolves to the one-argument copy and keeps the verbosity.
  def this(format: ResponseFormat) = this(format, None)

  def copy(
    format: ResponseFormat = this.format,
    verbosity: Option[Verbosity] = this.verbosity
  ): TextResponseConfig =
    new TextResponseConfig(format, verbosity)

  def copy(format: ResponseFormat): TextResponseConfig =
    new TextResponseConfig(format, this.verbosity)
}

// Binary compatibility with 2.0.0 / 1.7.1: on Scala 2 the synthetic companion of the one-field
// class was a Function1 (`opt.map(TextResponseConfig)`, `andThen`); a Scala 3 Mirror compiled
// against it passes one element to `fromProduct`. Only an explicit `unapply` call (Scala 2) - or
// a codec compiled against 2.0.0 that writes through it - sees the new pair.
object TextResponseConfig
    extends scala.runtime.AbstractFunction1[ResponseFormat, TextResponseConfig] {

  def apply(format: ResponseFormat): TextResponseConfig = new TextResponseConfig(format, None)

  def fromProduct(product: Product): TextResponseConfig =
    new TextResponseConfig(
      product.productElement(0).asInstanceOf[ResponseFormat],
      if (product.productArity > 1)
        product.productElement(1).asInstanceOf[Option[Verbosity]]
      else
        None
    )
}
