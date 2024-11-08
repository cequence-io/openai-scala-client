package io.cequence.openaiscala.anthropic.domain

sealed trait Content

sealed trait CacheControl
object CacheControl {
  case object Ephemeral extends CacheControl
}

trait Cacheable {
  def cacheControl: Option[CacheControl]
}

object Content {
  case class SingleString(
    text: String,
    override val cacheControl: Option[CacheControl] = None
  ) extends Content
      with Cacheable

  case class ContentBlocks(blocks: Seq[ContentBlockBase]) extends Content

  case class ContentBlockBase(
    content: ContentBlock,
    override val cacheControl: Option[CacheControl] = None
  ) extends Content
      with Cacheable

  sealed trait ContentBlock

  object ContentBlock {
    case class TextBlock(text: String) extends ContentBlock

    case class ImageBlock(
      `type`: String,
      mediaType: String,
      data: String
    ) extends ContentBlock

    // TODO: check PDF
  }
}
