package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.JsonSchema

/**
 * Defines a function in your own code the model can choose to call.
 *
 * @param name
 *   The name of the function to call.
 * @param parameters
 *   A JSON schema object describing the parameters of the function.
 * @param strict
 *   Whether to enforce strict parameter validation. Default true.
 * @param description
 *   Optional description of the function.
 */
case class FunctionTool(
  name: String,
  parameters: JsonSchema,
  strict: Boolean,
  description: Option[String] = None
) extends Tool {
  override val `type`: String = "function"
}
