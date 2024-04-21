package io.cequence.openaiscala.v2.domain

import io.cequence.openaiscala.domain.EnumValue

sealed trait SortOrder extends EnumValue

object SortOrder {
  case object asc extends SortOrder
  case object desc extends SortOrder
}
