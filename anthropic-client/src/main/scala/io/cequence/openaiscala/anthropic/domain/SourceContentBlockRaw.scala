package io.cequence.openaiscala.anthropic.domain

case class SourceContentBlockRaw(
  `type`: String, // document or image
  source: SourceBlockRaw,
  title: Option[String] = None,
  context: Option[String] = None,
  citations: Option[CitationsFlagRaw] = None
)

case class SourceBlockRaw(
  `type`: String,
  mediaType: Option[String] = None,
  data: Option[String] = None,
  content: Option[Seq[TextContentRaw]] = None
)

case class CitationsFlagRaw(
  enabled: Boolean
)

case class TextContentRaw(
  `type`: String,
  text: String
)
