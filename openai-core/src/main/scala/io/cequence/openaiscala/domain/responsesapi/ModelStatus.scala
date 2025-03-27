package io.cequence.openaiscala.domain.responsesapi

import io.cequence.wsclient.domain.EnumValue

/**
 * Represents the status of a model operation.
 */
sealed trait ModelStatus extends EnumValue

object ModelStatus {
  case object InProgress extends ModelStatus
  case object Completed extends ModelStatus
  case object Incomplete extends ModelStatus
  case object Searching extends ModelStatus
  case object Failed extends ModelStatus

  def values: Seq[ModelStatus] = Seq(
    InProgress,
    Completed,
    Incomplete,
    Searching,
    Failed
  )
}
