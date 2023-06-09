package io.cequence.openaiscala.domain.response

case class TranscriptResponse(
    text: String,
    verboseJson: Option[String] = None
)
