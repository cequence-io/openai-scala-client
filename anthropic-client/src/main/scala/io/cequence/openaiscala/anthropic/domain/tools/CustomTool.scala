package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.openaiscala.domain.JsonSchema

/**
 * Custom tool with user-defined input schema. Type is always "custom".
 *
 * @param name
 *   Name of the tool (1-128 characters). This is how the tool will be called by the model and
 *   in tool_use blocks.
 * @param inputSchema
 *   JSON schema for this tool's input. This defines the shape of the input that your tool
 *   accepts and that the model will produce.
 * @param description
 *   Description of what this tool does. Tool descriptions should be as detailed as possible.
 *   The more information that the model has about what the tool is and how to use it, the
 *   better it will perform.
 */
case class CustomTool(
  name: String,
  inputSchema: JsonSchema,
  description: Option[String] = None
) extends Tool {
  override val `type`: String = "custom"
}
