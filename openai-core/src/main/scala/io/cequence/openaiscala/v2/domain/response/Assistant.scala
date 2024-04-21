package io.cequence.openaiscala.v2.domain.response

import io.cequence.openaiscala.v2.domain.{AssistantId, AssistantTool, AssistantToolResource, FileId}

import java.{util => ju}

final case class Assistant(
  // The identifier, which can be referenced in API endpoints.
  id: AssistantId,

  // The Unix timestamp (in seconds) for when the assistant was created.
  created_at: ju.Date,

  // The name of the assistant. The maximum length is 256 characters.
  name: Option[String],

  // The description of the assistant. The maximum length is 512 characters.
  description: Option[String],

  // ID of the model to use. You can use the List models API to see all of your available models,
  // or see our Model overview for descriptions of them.
  model: String,

  // The system instructions that the assistant uses. The maximum length is 32768 characters.
  instructions: Option[String],

  // A list of tool enabled on the assistant. There can be a maximum of 128 tools per assistant.
  // Tools can be of types code_interpreter, retrieval, or function.
  tools: List[AssistantTool],

  // A set of resources that are used by the assistant's tools. The resources are specific to the type of tool.
  // For example, the code_interpreter tool requires a list of file IDs, while the file_search tool requires
  // a list of vector store IDs.
  tool_resources: List[AssistantToolResourceResponse],

  // Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional
  // information about the object in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
  metadata: Map[String, String] = Map(),

  // What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random,
  // while lower values like 0.2 will make it more focused and deterministic.
  temperature: Option[Double] = None,

  // An alternative to sampling with temperature, called nucleus sampling, where the model considers
  // the results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising
  // the top 10% probability mass are considered.
  //
  // We generally recommend altering this or temperature but not both.
  top_p: Option[Double] = None,

  // Specifies the format that the model must output. Compatible with GPT-4 Turbo and all GPT-3.5 Turbo models since `gpt-3.5-turbo-1106`.
  //
  // Setting to `{ "type": "json_object" }` enables JSON mode, which guarantees the message the model generates is valid JSON.
  //
  // Important: when using JSON mode, you must also instruct the model to produce JSON yourself via a system or user message.
  // Without this, the model may generate an unending stream of whitespace until the generation reaches the token limit,
  // resulting in a long-running and seemingly "stuck" request. Also note that the message content may be partially cut off
  // if finish_reason="length", which indicates the generation exceeded max_tokens or the conversation exceeded the max context length.
  response_format: ResponseFormat
)
