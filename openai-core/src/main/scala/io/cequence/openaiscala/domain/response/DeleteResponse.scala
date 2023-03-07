package io.cequence.openaiscala.domain.response

// note that practically we should always get only 'Deleted' or 'NotFound' status,
// but for completeness it's good to handle also 'NotDeleted'
sealed trait DeleteResponse

object DeleteResponse {
  case object Deleted extends DeleteResponse
  case object NotDeleted extends DeleteResponse
  case object NotFound extends DeleteResponse
}