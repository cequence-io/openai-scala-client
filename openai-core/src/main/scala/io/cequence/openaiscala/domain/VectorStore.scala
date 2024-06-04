package io.cequence.openaiscala.domain

import java.{util => ju}

case class VectorStore(
  id: String,
  name: String,
  status: String, // in_progress, completed, cancelled, failed
  usage_bytes: Long,
  file_counts: FileCounts,
  metadata: Map[String, String],
  created_at: java.util.Date,
  last_active_at: ju.Date,
//  last_used_at: ju.Date,
  expires_after: Option[ju.Date],
  expires_at: Option[ju.Date]
)

case class FileCounts(
  inProgress: Int,
  completed: Int,
  cancelled: Int,
  failed: Int,
  total: Int
)
