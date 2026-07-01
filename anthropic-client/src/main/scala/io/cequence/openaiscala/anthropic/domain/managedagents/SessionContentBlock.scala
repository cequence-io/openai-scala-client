package io.cequence.openaiscala.anthropic.domain.managedagents

/** Source for an image content block in a managed-agents session event. */
sealed trait SessionImageSource

object SessionImageSource {
  final case class Base64(
    data: String,
    mediaType: String
  ) extends SessionImageSource {
    val `type`: String = "base64"
  }
  final case class Url(url: String) extends SessionImageSource {
    val `type`: String = "url"
  }
  final case class FileRef(fileId: String) extends SessionImageSource {
    val `type`: String = "file"
  }
}

/** Source for a document content block in a managed-agents session event. */
sealed trait SessionDocumentSource

object SessionDocumentSource {
  final case class Base64(
    data: String,
    mediaType: String
  ) extends SessionDocumentSource {
    val `type`: String = "base64"
  }
  final case class PlainText(data: String) extends SessionDocumentSource {
    val `type`: String = "text"
    val mediaType: String = "text/plain"
  }
  final case class Url(url: String) extends SessionDocumentSource {
    val `type`: String = "url"
  }
  final case class FileRef(fileId: String) extends SessionDocumentSource {
    val `type`: String = "file"
  }
}

/**
 * A content block carried by a session event sent to a Managed Agent. Mirrors the Messages-API
 * blocks but is modeled independently because the managed-agents event schema is its own union
 * (image/document sources include `url` and `file` variants, plus a `search_result` block).
 */
sealed trait SessionContentBlock

object SessionContentBlock {

  final case class Text(text: String) extends SessionContentBlock {
    val `type`: String = "text"
  }

  final case class Image(source: SessionImageSource) extends SessionContentBlock {
    val `type`: String = "image"
  }

  final case class Document(
    source: SessionDocumentSource,
    title: Option[String] = None,
    context: Option[String] = None
  ) extends SessionContentBlock {
    val `type`: String = "document"
  }

  /** A search-result block (valid only inside tool-result events). */
  final case class SearchResult(
    title: String,
    source: String,
    content: Seq[Text],
    citationsEnabled: Option[Boolean] = None
  ) extends SessionContentBlock {
    val `type`: String = "search_result"
  }
}
