package io.cequence.openaiscala.v2.domain

import java.{util => ju}

case class ThreadFullMessage(
  // The identifier, which can be referenced in API endpoints.
  id: String,

  // The Unix timestamp (in seconds) for when the message was created.
  created_at: ju.Date,

  // The thread ID that this message belongs to.
  thread_id: String,

  // The entity that produced the message. One of user or assistant.
  role: ChatRole = ChatRole.User,

  // The content of the message in an array of text and/or images.
  content: Seq[ThreadMessageContent] = Nil,

  // If applicable, the ID of the assistant that authored this message. Null if not applicable.
  assistant_id: Option[String] = None,

  // If applicable, the ID of the run associated with the authoring of this message. Null if not applicable.
  run_id: Option[String] = None,

  // A list of file IDs that the assistant should use.
  // Useful for tools like retrieval and code_interpreter that can access files.
  // A maximum of 10 files can be attached to a message.
  file_ids: Seq[String] = Nil,

  // Set of 16 key-value pairs that can be attached to an object.
  // This can be useful for storing additional information about the object in a structured format.
  // Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.
  metadata: Map[String, String] = Map()
)

case class ThreadMessageContent(
  `type`: ThreadMessageContentType,
  image_file: Option[FileId],
  text: Option[ThreadMessageText]
)

sealed trait ThreadMessageContentType extends EnumValue

object ThreadMessageContentType {
  case object image_file extends ThreadMessageContentType
  case object text extends ThreadMessageContentType
}

case class ThreadMessageText(
  value: String,
  annotations: Seq[FileAnnotation]
)

case class FileAnnotation(
  `type`: FileAnnotationType,
  file_citation: Option[FileCitation],
  file_path: Option[FileId],
  // The text in the message content that needs to be replaced.
  text: String,
  start_index: Int,
  end_index: Int
)

sealed trait FileAnnotationType extends EnumValue

object FileAnnotationType {
  // A citation within the message that points to a specific quote from a specific File associated with the assistant or the message.
  // Generated when the assistant uses the "retrieval" tool to search files.
  case object file_citation extends FileAnnotationType
  // A URL for the file that's generated when the assistant used the code_interpreter tool to generate a file
  case object file_path extends FileAnnotationType
}

case class FileId(
  file_id: String
)

case class FileCitation(
  // The ID of the specific File the citation is from.
  file_id: String,
  // The specific quote in the file.
  quote: String
)
