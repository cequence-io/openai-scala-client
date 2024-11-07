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
  case class SingleString(text: String, override val cacheControl: Option[CacheControl] = None) extends Content
      with Cacheable

  case class ContentBlocks(blocks: Seq[ContentBlock]) extends Content

  sealed trait ContentBlock

  object ContentBlock {
    case class TextBlock(text: String, override val cacheControl: Option[CacheControl] = None)
      extends ContentBlock
        with Cacheable

    case class ImageBlock(
      `type`: String,
      mediaType: String,
      data: String
    ) extends ContentBlock

    // TODO: check PDF
  }
}
