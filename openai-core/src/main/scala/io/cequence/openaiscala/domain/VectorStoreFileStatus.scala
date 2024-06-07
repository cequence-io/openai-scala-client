package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.NamedEnumValue

sealed abstract class VectorStoreFileStatus(value: String) extends NamedEnumValue(value)

object VectorStoreFileStatus {
  case object InProgress extends VectorStoreFileStatus("in_progress")
  case object Completed extends VectorStoreFileStatus("completed")
  case object Failed extends VectorStoreFileStatus("failed")
  case object Cancelled extends VectorStoreFileStatus("cancelled")
}
