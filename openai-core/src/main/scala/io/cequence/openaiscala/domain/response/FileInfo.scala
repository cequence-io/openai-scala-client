package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class FileInfo(
  id: String,
  bytes: Long,
  created_at: ju.Date,
  filename: String,
  purpose: String,
  status: String, // uploaded, processed, pending, error, deleting or deleted
  status_details: Option[String]
)
