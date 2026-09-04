package io.cequence.openaiscala.anthropic.domain.response

import io.cequence.openaiscala.domain.HasType
import play.api.libs.json.{JsObject, JsValue}

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

  case class DeltaInputJson(
    partial_json: String
  ) extends DeltaBlock {
    override val `type`: String = "input_json_delta"
  }

  case class DeltaCitations(
    citation: JsValue
  ) extends DeltaBlock {
    override val `type`: String = "citations_delta"
  }

  /** Forward-compatible fallback for a delta block type this client doesn't model yet. */
  case class DeltaUnknown(
    override val `type`: String,
    raw: JsObject
  ) extends DeltaBlock
}
