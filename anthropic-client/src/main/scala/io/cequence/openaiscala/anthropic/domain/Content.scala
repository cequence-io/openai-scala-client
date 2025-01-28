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
    case class TextBlock(
      text: String,
      citations: Seq[Citation] = Nil
    ) extends ContentBlock

    case class Citation(
      `type`: String,
      citedText: String,
      documentIndex: Int,
      documentTitle: Option[String],
      startCharIndex: Option[Int],
      endCharIndex: Option[Int],
      startBlockIndex: Option[Int],
      endBlockIndex: Option[Int]
    )

    case class MediaBlock(
      `type`: String,
      encoding: String,
      mediaType: String,
      data: String,
      title: Option[String] = None, // Document Title
      context: Option[String] = None, // Context about the document that will not be cited from
      citations: Option[Boolean] = None
    ) extends ContentBlock

    case class TextsContentBlock(
      texts: Seq[String],
      title: Option[String] = None, // Document Title
      context: Option[String] = None, // Context about the document that will not be cited from
      citations: Option[Boolean] = None
    ) extends ContentBlock

    object MediaBlock {
      def pdf(
        data: String,
        cacheControl: Option[CacheControl] = None,
        title: Option[String] = None,
        context: Option[String] = None,
        citations: Boolean = false
      ): ContentBlockBase =
        ContentBlockBase(
          MediaBlock(
            "document",
            "base64",
            "application/pdf",
            data,
            title,
            context,
            Some(citations)
          ),
          cacheControl
        )

      def txt(
        data: String,
        cacheControl: Option[CacheControl] = None,
        title: Option[String] = None,
        context: Option[String] = None,
        // https://docs.anthropic.com/en/docs/build-with-claude/citations
        citations: Boolean = false
      ): ContentBlockBase =
        ContentBlockBase(
          MediaBlock("document", "text", "text/plain", data, title, context, Some(citations)),
          cacheControl
        )

      def txts(
        contents: Seq[String],
        cacheControl: Option[CacheControl] = None,
        title: Option[String] = None,
        context: Option[String] = None,
        citations: Boolean = false
      ): ContentBlockBase =
        ContentBlockBase(
          TextsContentBlock(contents, title, context, Some(citations)),
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

  }
}
