package io.cequence.openaiscala.gemini.domain

/**
 * A file uploaded to the Gemini Files API (`files/{id}`). Files are retained for 48 hours and
 * count towards a 20 GB per-project storage cap.
 *
 * @param name
 *   Resource name, format `files/{id}`.
 * @param state
 *   Processing state: `PROCESSING`, `ACTIVE` (usable), or `FAILED`.
 * @see
 *   <a href="https://ai.google.dev/api/files">Gemini Files API Docs</a>
 */
final case class GeminiFile(
  name: String,
  displayName: Option[String] = None,
  mimeType: Option[String] = None,
  sizeBytes: Option[Long] = None,
  state: Option[String] = None,
  uri: Option[String] = None,
  createTime: Option[String] = None,
  expirationTime: Option[String] = None
)
