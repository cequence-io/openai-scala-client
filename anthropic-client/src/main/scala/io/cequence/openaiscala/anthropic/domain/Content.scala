package io.cequence.openaiscala.anthropic.domain

sealed trait Content

object Content {
  case class TextContent(text: String) extends Content
  // case class ImageContent(text: String) extends Content

}
