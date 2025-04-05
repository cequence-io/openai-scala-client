package io.cequence.openaiscala.domain.responsesapi

final case class InputItemsResponse(
  firstId: String,
  lastId: String,
  hasMore: Boolean,
  data: Seq[Input]
)
