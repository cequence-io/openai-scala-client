package io.cequence.openaiscala.domain.response

case class TracedBlock(
  blockType: String,
  text: Option[String],
  summary: String,
  originalBlock: Any
)
