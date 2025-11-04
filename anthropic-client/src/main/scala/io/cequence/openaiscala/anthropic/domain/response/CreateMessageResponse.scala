package io.cequence.openaiscala.anthropic.domain.response

import io.cequence.openaiscala.anthropic.domain.{
  BashCodeExecutionToolResultContent,
  ChatRole,
  Content
}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{
  BashCodeExecutionToolResultBlock,
  Citation,
  TextBlock,
  ThinkingBlock,
  ToolUseBlock
}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.wsclient.domain.NamedEnumValue

final case class CreateMessageResponse(
  id: String,
  role: ChatRole,
  content: ContentBlocks,
  model: String,
  stop_reason: Option[String],
  stop_sequence: Option[String],
  usage: UsageInfo
) {
  def blockContents: Seq[Content.ContentBlock] =
    content.blocks.map(_.content)

  def texts: Seq[String] =
    textsWithCitations.map(_._1)

  def citations: Seq[Seq[Citation]] =
    textsWithCitations.map(_._2)

  def textsWithCitations: Seq[(String, Seq[Citation])] =
    blockContents.collect { case TextBlock(text, citations) =>
      (text, citations)
    }

  def text: String = texts.mkString("")

  def toolUseBlocks: Seq[ToolUseBlock] =
    blockContents.collect { case x: ToolUseBlock => x }

  def bashCodeExecutionToolFileIds: Seq[Seq[String]] = blockContents.collect {
    case BashCodeExecutionToolResultBlock(
          BashCodeExecutionToolResultContent.Success(content, _, _, _),
          _
        ) =>
      content.map(_.fileId)
  }

  def thinkingBlocks: Seq[String] =
    blockContents.collect { case ThinkingBlock(text, _) => text }

  def thinkingText: String = thinkingBlocks.mkString("")
}

object CreateMessageResponse {

  sealed abstract class StopReason(name: String) extends NamedEnumValue(name)

  /** The model reached a natural stopping point. */
  case object EndTurn extends StopReason("end_turn")

  /** We exceeded the requested `max_tokens`` or the model's maximum. */
  case object MaxTokens extends StopReason("max_tokens") // max_tokens? or length

  /** One of your provided custom `stop_sequences`` was generated. */
  case object StopSequence extends StopReason("stop_sequence")

  case class UsageInfo(
    input_tokens: Int,
    output_tokens: Int,
    cache_creation_input_tokens: Option[Int],
    cache_read_input_tokens: Option[Int]
  )
}
