package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.JsonSchema

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

case class TextResponseConfig(
  format: ResponseFormat
)
