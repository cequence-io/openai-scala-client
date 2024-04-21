package io.cequence.openaiscala.v2.domain

sealed trait Content

case class TextContent(
  text: String
) extends Content

case class ImageURLContent(
  url: String
) extends Content
