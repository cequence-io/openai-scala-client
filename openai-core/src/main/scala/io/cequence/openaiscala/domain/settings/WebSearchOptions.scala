package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.EnumValue

case class WebSearchOptions(
  user_location: Option[UserLocation] = None,
  search_context_size: Option[SearchContextSize] = None
)

case class UserLocation(
  `type`: String = "approximate",
  approximate: ApproximateLocation
)

case class ApproximateLocation(
  country: String,
  city: String,
  region: String
)

sealed trait SearchContextSize extends EnumValue

object SearchContextSize {
  case object low extends SearchContextSize
  case object medium extends SearchContextSize
  case object high extends SearchContextSize

  def values: Seq[SearchContextSize] = Seq(low, medium, high)
}
