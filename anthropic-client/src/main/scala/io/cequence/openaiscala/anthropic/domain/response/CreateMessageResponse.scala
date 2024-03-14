package io.cequence.openaiscala.anthropic.domain.response

import io.cequence.openaiscala.anthropic.domain.ChatRole
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.domain.NamedEnumValue

final case class CreateMessageResponse(
  id: String,
  role: ChatRole,
  content: ContentBlocks,
  model: String,
  stop_reason: Option[String],
  stop_sequence: Option[String],
  usage: UsageInfo
)

object CreateMessageResponse {

  sealed abstract class StopReason(name: String) extends NamedEnumValue(name)

  /** The model reached a natural stopping point. */
  case object EndTurn extends StopReason("end_turn")

  /** We exceeded the requested `max_tokens`` or the model's maximum. */
  case object MaxTokens extends StopReason("max_tokens")

  /** One of your provided custom `stop_sequences`` was generated. */
  case object StopSequence extends StopReason("stop_sequence")

  case class UsageInfo(
    input_tokens: Int,
    output_tokens: Int
  )
}
