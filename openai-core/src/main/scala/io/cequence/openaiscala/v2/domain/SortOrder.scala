package io.cequence.openaiscala.v2.domain

sealed trait SortOrder extends EnumValue

object SortOrder {
  case object asc extends SortOrder
  case object desc extends SortOrder
}
