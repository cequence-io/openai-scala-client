package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class FileInfo(
  id: String,
  bytes: Long,
  created_at: ju.Date,
  updated_at: Option[ju.Date],
  filename: String,
  purpose: String,
  status: String, // uploaded, processed, pending, error, deleting or deleted
  status_details: Option[String],
  statistics: Option[FileStatistics]
)

case class FileStatistics(
  // The number of contained training examples in files of kind "fine-tune" once validation of file content is complete.
  examples: Int,
  // The number of tokens used in prompts and completions for files of kind "fine-tune" once validation of file content is complete.
  tokens: Int
)
