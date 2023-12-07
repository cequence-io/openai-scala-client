package io.cequence.openaiscala.domain

import java.{util => ju}

case class ThreadMessageFile(
  // The identifier, which can be referenced in API endpoints.
  id: String,

  // The Unix timestamp(in seconds), for when the message file was created
  created_at: ju.Date,

  // The ID of the message that the File is attached to
  message_id: String
)
