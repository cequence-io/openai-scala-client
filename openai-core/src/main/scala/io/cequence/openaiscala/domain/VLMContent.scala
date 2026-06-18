package io.cequence.openaiscala.domain

/**
 * Provider-uniform attachment shape for VLM (Vision-Language Model) chat completions.
 *
 * Every file is sent as a pair of content blocks: a `[file: NAME]` text label followed by the
 * file content itself. The label gives the model a stable identifier to refer back to in its
 * response — important because:
 *
 *   - OpenAI carries `FileContent.filename` on the wire for PDFs, but Anthropic / Bedrock /
 *     Gemini / Vertex do not pass through any filename for `FileContent` or `ImageURLContent`;
 *     without the label, the model would have to invent names ("image 1", "the PDF") or
 *     hallucinate the original filename.
 *   - OpenAI requires `ImageURLContent` for JPEG / PNG (it rejects `FileContent` for image
 *     MIME types). Anthropic / Bedrock / Gemini / Vertex accept either. The single source of
 *     truth for "which envelope to use" lives here in [[contentOf]].
 *
 * To wire this into a chat completion: build a `UserSeqMessage` whose `content` starts with
 * the user prompt text and is then extended with `files.flatMap(VLMContent.of)`. Pair this
 * with one system-prompt clause:
 *
 * {{{
 *   "Each attached file is preceded by a label of the form '[file: NAME]' (or "
 *   "'[file #INDEX: NAME]' when indexed). When referring to a file in your answer, use "
 *   "exactly the NAME from its label. Do not invent or alter filenames."
 * }}}
 *
 * That convention works uniformly across all major providers.
 */
object VLMContent {

  // Note: provider support for image formats varies. JPEG / PNG / GIF / WebP are accepted by
  // all major providers. BMP and TIFF are NOT universally supported (e.g. OpenAI and Anthropic
  // reject them) — they are included here so callers targeting providers that do accept them
  // (or that transcode upstream) can pass them through; convert to PNG/JPEG for portability.
  private val ImageMimeByExt: Map[String, String] = Map(
    "jpg" -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "png" -> "image/png",
    "webp" -> "image/webp",
    "gif" -> "image/gif",
    "bmp" -> "image/bmp",
    "tif" -> "image/tiff",
    "tiff" -> "image/tiff"
  )

  private def extensionOf(fileName: String): String = {
    val dot = fileName.lastIndexOf('.')
    if (dot < 0) "" else fileName.substring(dot + 1).toLowerCase
  }

  private def dataUrl(
    mime: String,
    contentBase64: String
  ): String = s"data:$mime;base64,$contentBase64"

  private def contentOf(
    contentBase64: String,
    fileName: String
  ): Content =
    extensionOf(fileName) match {
      case "pdf" =>
        FileContent(
          fileData = Some(dataUrl("application/pdf", contentBase64)),
          filename = Some(fileName)
        )
      case ext if ImageMimeByExt.contains(ext) =>
        ImageURLContent(dataUrl(ImageMimeByExt(ext), contentBase64))
      case other =>
        throw new IllegalArgumentException(
          s"Unsupported VLM input extension '.$other' (file: $fileName)"
        )
    }

  /**
   * Returns the labeled pair `[file: NAME]` + file content for a file whose payload is already
   * base64-encoded. Always use this — never the raw content alone — so the model sees a
   * uniform shape across providers.
   *
   * @param fileIndex
   *   optional index included in the label (`[file #INDEX: NAME]`) — useful to disambiguate
   *   multiple attachments with the same (or no meaningful) filename.
   */
  def of(
    contentBase64: String,
    fileName: String,
    fileIndex: Option[Int] = None
  ): Seq[Content] = {
    val label = fileIndex match {
      case Some(index) => s"[file #$index: $fileName]"
      case None        => s"[file: $fileName]"
    }

    Seq(
      TextContent(label),
      contentOf(contentBase64, fileName)
    )
  }

  /**
   * Convenience overload for callers that have raw bytes (e.g., read from disk).
   *
   * Note: Scala allows default arguments on only one overloaded alternative, so `fileIndex`
   * has no default here - use the two-arg overload below when there is no index.
   */
  def of(
    bytes: Array[Byte],
    fileName: String,
    fileIndex: Option[Int]
  ): Seq[Content] =
    of(
      java.util.Base64.getEncoder.encodeToString(bytes),
      fileName,
      fileIndex
    )

  def of(
    bytes: Array[Byte],
    fileName: String
  ): Seq[Content] =
    of(bytes, fileName, fileIndex = None)
}
