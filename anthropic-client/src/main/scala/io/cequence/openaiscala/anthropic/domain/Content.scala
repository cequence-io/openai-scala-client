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
  }
}
