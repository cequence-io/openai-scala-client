package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{AssistantId, FileId}

import java.{util => ju}

final case class AssistantFile(
  // The identifier, which can be referenced in API endpoints.
  id: FileId,
  // The object type, which is always assistant.file.
  `object`: String,
  // The Unix timestamp (in seconds) for when the assistant file was created.
  created_at: ju.Date,
  // The assistant ID that the file is attached to.
  assistant_id: AssistantId
)
