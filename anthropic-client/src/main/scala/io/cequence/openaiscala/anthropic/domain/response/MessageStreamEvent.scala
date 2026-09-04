package io.cequence.openaiscala.anthropic.domain.response

import io.cequence.openaiscala.anthropic.domain.Content
import play.api.libs.json.JsValue

/**
 * A single raw SSE event of the Anthropic streaming Messages API
 * (https://docs.anthropic.com/en/api/messages-streaming). This is a superset of
 * [[ContentBlockDelta]] - it also carries message metadata (id, model, usage), the
 * stop_reason/usage delta and block start/stop markers, which [[ContentBlockDelta]] alone
 * cannot represent.
 */
sealed trait MessageStreamEvent

object MessageStreamEvent {

  /** First event of a stream - carries the message shell (id, model, role, initial usage). */
  case class MessageStart(message: CreateMessageResponse) extends MessageStreamEvent

  /**
   * content_block_start; `contentBlock` is None when the block could not be parsed (e.g. a
   * thinking block start has no signature yet) - `blockType` is always set.
   */
  case class ContentBlockStart(
    index: Int,
    blockType: String,
    contentBlock: Option[Content.ContentBlock]
  ) extends MessageStreamEvent

  case class ContentBlockDeltaEvent(delta: ContentBlockDelta) extends MessageStreamEvent

  case class ContentBlockStop(index: Int) extends MessageStreamEvent

  /** Carries the final stop reason/sequence and the incremental (output-only) usage. */
  case class MessageDelta(
    stopReason: Option[String],
    stopSequence: Option[String],
    usage: Option[MessageDeltaUsage]
  ) extends MessageStreamEvent

  case object MessageStop extends MessageStreamEvent

  case object Ping extends MessageStreamEvent

  /** Any event type this client doesn't model yet - forward compatible, never an error. */
  case class UnknownEvent(
    eventType: String,
    raw: JsValue
  ) extends MessageStreamEvent
}

case class MessageDeltaUsage(
  output_tokens: Int,
  input_tokens: Option[Int] = None,
  cache_creation_input_tokens: Option[Int] = None,
  cache_read_input_tokens: Option[Int] = None
)
