package io.cequence.openaiscala.domain

sealed class Content

case class TextContent(
  text: String
) extends Content

case class ImageURLContent(
  url: String
) extends Content
