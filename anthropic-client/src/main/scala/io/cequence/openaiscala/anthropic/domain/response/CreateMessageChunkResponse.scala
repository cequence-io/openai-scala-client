package io.cequence.openaiscala.anthropic.domain.response

import io.cequence.openaiscala.domain.HasType

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
    case _                          => ""
  }
}

sealed trait DeltaBlock extends HasType

object DeltaBlock {

  case class DeltaText(
    text: String
  ) extends DeltaBlock {
    override val `type`: String = "text_delta"
  }

  case class DeltaThinking(
    thinking: String
  ) extends DeltaBlock {
    override val `type`: String = "thinking_delta"
  }

  case class DeltaSignature(
    signature: String
  ) extends DeltaBlock {
    override val `type`: String = "signature_delta"
  }
}
