package io.cequence.openaiscala.vertexai.domain

/**
 * Tool configuration for Vertex AI.
 */
sealed trait Tool

object Tool {

  /**
   * A list of function declarations that the model may use to generate function calls.
   */
  case class FunctionDeclarations(
    functionDeclarations: Seq[FunctionDeclaration]
  ) extends Tool

  /**
   * Tool that enables Google Search grounding for the model's responses.
   */
  case object GoogleSearch extends Tool

  /**
   * Tool that enables the model to generate and execute Python code.
   */
  case object CodeExecution extends Tool
}

/**
 * Structured representation of a function declaration as defined by the OpenAPI 3.03
 * specification.
 *
 * @param name
 *   Required. The name of the function. Must be a-z, A-Z, 0-9, or contain underscores and
 *   dashes, with a maximum length of 63.
 * @param description
 *   Required. A brief description of the function.
 * @param parameters
 *   Optional. Describes the parameters to this function.
 */
case class FunctionDeclaration(
  name: String,
  description: String,
  parameters: Option[Schema] = None
)
