package io.cequence.openaiscala.anthropic.domain

sealed trait Content

object Content {
  case class SingleString(text: String) extends Content

  case class ContentBlocks(blocks: Seq[ContentBlock]) extends Content

  sealed trait ContentBlock

  object ContentBlock {
    case class TextBlock(text: String) extends ContentBlock
    case class ImageBlock(
      `type`: String,
      mediaType: String,
      data: String
    ) extends ContentBlock

    case class ToolUseBlock(
      id: String,
      name: String,
      input: Map[String, Any] // TODO: allow here only Text content blocks
    ) extends ContentBlock

//    sealed trait ToolUseBlock extends ContentBlock
//    // TODO: allow only for responses to createChatToolCompletion
//    case class ToolUseBlockSuccess(
//      toolUseId: String,
//      content: String // TODO: allow here only Text content blocks
//    ) extends ToolUseBlock
//
//    case class ToolUseBlockFailure(
//      toolUseId: String,
//      content: String // TODO: allow here only Text content blocks
//    ) extends ToolUseBlock
  }
}
