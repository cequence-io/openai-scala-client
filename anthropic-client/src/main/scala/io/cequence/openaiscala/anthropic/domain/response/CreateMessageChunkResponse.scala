package io.cequence.openaiscala.anthropic.domain.response

case class CreateMessageChunkResponse(
  `type`: String,
  message: CreateMessageResponse
)

case class ContentBlockDelta(
  `type`: String,
  index: Int,
  delta: DeltaBlock
) {
  def text: String = delta match {
    case DeltaBlock.DeltaText(text) => text
    case _ => ""
  }
}

sealed trait DeltaBlock

object DeltaBlock {

  case class DeltaText(
    text: String
  ) extends DeltaBlock

  case class DeltaThinking(
    thinking: String
  ) extends DeltaBlock

  case class DeltaSignature(
    signature: String
  ) extends DeltaBlock
}
