package io.cequence.openaiscala.v2.domain.response

case class TranscriptResponse(
  text: String,
  verboseJson: Option[String] = None
)
