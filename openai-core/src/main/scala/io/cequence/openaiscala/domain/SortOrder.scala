package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait SortOrder extends EnumValue

object SortOrder {
  case object asc extends SortOrder
  case object desc extends SortOrder
}
