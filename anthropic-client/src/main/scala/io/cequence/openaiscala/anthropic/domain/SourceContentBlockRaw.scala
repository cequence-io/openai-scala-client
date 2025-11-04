package io.cequence.openaiscala.anthropic.domain

case class SourceContentBlockRaw(
  source: SourceBlockRaw,
  title: Option[String] = None,
  context: Option[String] = None,
  citations: Option[CitationsFlagRaw] = None
)

case class SourceBlockRaw(
  `type`: String,
  mediaType: Option[String] = None,
  data: Option[String] = None,
  content: Option[Seq[TextContentRaw]] = None,
  fileId: Option[String] = None
)

case class CitationsFlagRaw(
  enabled: Boolean
)

case class TextContentRaw(
  `type`: String,
  text: String
)
