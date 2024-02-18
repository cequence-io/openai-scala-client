package io.cequence.openaiscala.domain

sealed trait ToolSpec

sealed trait AssistantTool

case object CodeInterpreterSpec extends AssistantTool

case object RetrievalSpec extends AssistantTool

case class FunctionSpec(
  // The name of the function to be called.
  // Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
  name: String,

  // The description of what the function does.
  description: Option[String] = None,

  // The parameters the functions accepts, described as a JSON Schema object.
  // See the guide for examples, and the JSON Schema reference for documentation about the format.
  parameters: Map[String, Any]
) extends ToolSpec
    with AssistantTool
