package io.cequence.openaiscala.domain

sealed trait AssistantTool

sealed trait ChatCompletionTool

object AssistantTool {
  case object CodeInterpreterTool extends AssistantTool

  final case class FileSearchTool(maxNumResults: Option[Int] = None) extends AssistantTool

  case class FunctionTool(
    // The name of the function to be called.
    // Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
    name: String,

    // The description of what the function does.
    description: Option[String] = None,

    // The parameters the functions accepts, described as a JSON Schema object.
    // See the guide for examples, and the JSON Schema reference for documentation about the format.
    parameters: Map[String, Any] = Map.empty, // TODO: support JsonSchema out of box

    //  Whether to enable strict schema adherence when generating the function call. If set to true, the model will
    //  follow the exact schema defined in the parameters field. Only a subset of JSON Schema is supported when strict
    //  is true. Learn more about Structured Outputs in the function calling guide.
    strict: Option[Boolean] = None
  ) extends AssistantTool
      with ChatCompletionTool
}
