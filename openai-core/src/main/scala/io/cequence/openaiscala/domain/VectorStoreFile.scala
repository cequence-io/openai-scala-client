package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait LastErrorCode extends EnumValue

object LastErrorCode {
  case object ServerError extends LastErrorCode
  case object RateLimitExceeded extends LastErrorCode
}

case class LastError(
  code: LastErrorCode,
  message: String
)

case class VectorStoreFile(
  id: String,
  `object`: String,
  usageBytes: Int,
  createdAt: Int,
  vectorStoreId: String,
  status: VectorStoreFileStatus,
  lastError: Option[LastError],
  chunkingStrategy: ChunkingStrategy
)
