package io.cequence.openaiscala.domain

sealed trait ForcableTool

sealed trait ToolSpec

sealed trait MessageTool

sealed trait AssistantTool

case object CodeInterpreterSpec extends AssistantTool with MessageTool with ForcableTool

case object FileSearchSpec extends AssistantTool with MessageTool with ForcableTool

/**
 * @param name
 *   The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and
 *   dashes, with a maximum length of 64.
 * @param description
 *   The description of what the function does.
 * @param strict
 *   Turn on Structured Outputs. See <a
 *   href="https://openai.com/index/introducing-structured-outputs-in-the-api/">Structured
 *   Outputs</a>
 * @param parameters
 *   The parameters the functions accepts, described as a JSON Schema object. See the guide for
 *   examples, and the JSON Schema reference for documentation about the format.
 */
case class FunctionSpec(
  name: String,
  description: Option[String] = None,
  strict: Option[Boolean] = Some(false),
  parameters: Map[String, Any]
) extends ToolSpec
    with AssistantTool
    with ForcableTool
