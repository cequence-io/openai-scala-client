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

    case class MediaBlock(
      `type`: String,
      encoding: String,
      mediaType: String,
      data: String
    ) extends ContentBlock

    object MediaBlock {
      def pdf(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase =
        ContentBlockBase(
          MediaBlock("document", "base64", "application/pdf", data),
          cacheControl
        )

      def image(
        mediaType: String
      )(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase =
        ContentBlockBase(MediaBlock("image", "base64", mediaType, data), cacheControl)

      def jpeg(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/jpeg")(data, cacheControl)

      def png(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/png")(data, cacheControl)

      def gif(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/gif")(data, cacheControl)

      def webp(
        data: String,
        cacheControl: Option[CacheControl] = None
      ): ContentBlockBase = image("image/webp")(data, cacheControl)
    }

    case class ImageBlock(
      `type`: String,
      mediaType: String,
      data: String
    ) extends ContentBlock

    case class DocumentBlock(
      `type`: String,
      mediaType: String,
      data: String
    ) extends ContentBlock

    object DocumentBlock {
      def pdf(
        data: String,
        cacheControl: Option[CacheControl]
      ): ContentBlockBase =
        ContentBlockBase(DocumentBlock("base64", "application/pdf", data), cacheControl)
    }

    object ImageBlock {
      def jpeg(
        data: String,
        cacheControl: Option[CacheControl]
      ): ContentBlockBase =
        ContentBlockBase(ImageBlock("base64", "image/jpeg", data), cacheControl)

      def png(
        data: String,
        cacheControl: Option[CacheControl]
      ): ContentBlockBase =
        ContentBlockBase(ImageBlock("base64", "image/png", data), cacheControl)

      def gif(
        data: String,
        cacheControl: Option[CacheControl]
      ): ContentBlockBase =
        ContentBlockBase(ImageBlock("base64", "image/gif", data), cacheControl)

      def webp(
        data: String,
        cacheControl: Option[CacheControl]
      ): ContentBlockBase =
        ContentBlockBase(ImageBlock("base64", "image/webp", data), cacheControl)
    }

    // TODO: check PDF
  }
}
