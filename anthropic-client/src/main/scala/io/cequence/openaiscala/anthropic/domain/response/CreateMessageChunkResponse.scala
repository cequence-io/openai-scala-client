package io.cequence.openaiscala.anthropic.domain.response

case class CreateMessageChunkResponse(
  `type`: String,
  message: CreateMessageResponse
)

case class ContentBlockDelta(
  `type`: String,
  index: Int,
  delta: DeltaText
)

case class DeltaText(
  text: String
)
