package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{AssistantId, AssistantTool, FileId}

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

  // A list of file IDs attached to this assistant. There can be a maximum of 20 files attached to the assistant.
  // Files are ordered by their creation date in ascending order.
  file_ids: List[FileId],

  // Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional
  // information about the object in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
  metadata: Map[String, String] = Map()
)
