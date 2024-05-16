package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.AssistantToolResourceResponse

import java.{util => ju}

case class Thread(
  // The identifier, which can be referenced in API endpoints.
  id: String,

  // The Unix timestamp (in seconds) for when the thread was created.
  created_at: ju.Date,

  // A set of resources that are made available to the assistant's tools in this thread.
  // The resources are specific to the type of tool. For example, the code_interpreter tool requires a list of file IDs,
  // while the file_search tool requires a list of vector store IDs.
  tool_resources: Seq[AssistantToolResourceResponse] = Nil,

  // Set of 16 key-value pairs that can be attached to an object.
  // This can be useful for storing additional information about the object in a structured format.
  // Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.
  metadata: Map[String, String] = Map()
)

case class ThreadMessage(
  // The content of the message.
  content: String,

  // The role of the entity that is creating the message.
  // Currently, only "user" is supported.
  role: ChatRole = ChatRole.User,

  // A list of File IDs that the message should use.
  // There can be a maximum of 10 files attached to a message.
  // Useful for tools like retrieval and code_interpreter that can access and use files.
  file_ids: Seq[String] = Nil,

  // Set of 16 key-value pairs that can be attached to an object.
  // This can be useful for storing additional information about the object in a structured format.
  // Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.
  metadata: Map[String, String] = Map()
)
