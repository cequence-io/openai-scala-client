package io.cequence.openaiscala.anthropic.domain

import io.cequence.openaiscala.domain.{HasType, JsonSchema}

/**
 * Defines the format for structured output from Claude.
 *
 * @see
 *   [[https://docs.anthropic.com/claude/docs/structured-outputs Anthropic Structured Outputs Documentation]]
 */
sealed trait OutputFormat extends HasType

object OutputFormat {

  /**
   * JSON Schema output format for structured responses.
   *
   * @param schema
   *   The JSON schema that defines the structure of the output
   */
  case class JsonSchemaFormat(
    schema: JsonSchema
  ) extends OutputFormat {
    override val `type`: String = "json_schema"
  }
}
