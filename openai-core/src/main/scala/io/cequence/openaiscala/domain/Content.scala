package io.cequence.openaiscala.domain

sealed trait Content

case class TextContent(
  text: String
) extends Content

case class ImageURLContent(
  url: String
) extends Content

/**
 * A file input part of a user message (e.g. a PDF).
 *
 * Exactly one of `fileId` or `fileData` should be set. `fileId` references a file already
 * uploaded via the Files API; `fileData` carries inline base64-encoded bytes (a data URL or
 * raw base64 depending on the provider — for OpenAI a data URL like
 * `data:application/pdf;base64,...` is expected). `filename` is required when sending
 * `fileData`.
 */
case class FileContent(
  fileId: Option[String] = None,
  fileData: Option[String] = None,
  filename: Option[String] = None
) extends Content
