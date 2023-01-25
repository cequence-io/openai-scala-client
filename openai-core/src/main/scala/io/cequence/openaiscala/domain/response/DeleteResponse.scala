package io.cequence.openaiscala.domain.response

// note that practically we should always get only 'Deleted' or 'NotFound' status,
// but for completeness it's good to handle also 'NotDeleted'
object DeleteResponse extends Enumeration {
  val Deleted, NotDeleted, NotFound = Value
}
