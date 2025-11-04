package io.cequence.openaiscala.anthropic.domain

import io.cequence.openaiscala.domain.HasType
import java.util.Date

/**
 * Metadata for a file uploaded to Anthropic.
 *
 * @param id
 *   Unique object identifier. The format and length of IDs may change over time.
 * @param filename
 *   Original filename of the uploaded file.
 * @param mimeType
 *   MIME type of the file.
 * @param sizeBytes
 *   Size of the file in bytes.
 * @param createdAt
 *   RFC 3339 datetime representing when the file was created.
 * @param downloadable
 *   Whether the file can be downloaded.
 */
case class FileMetadata(
  id: String,
  filename: String,
  mimeType: String,
  sizeBytes: Int,
  createdAt: Date,
  downloadable: Boolean = false
) extends HasType {
  val `type`: String = "file"
}

/**
 * Response from listing files.
 *
 * @param data
 *   List of file metadata objects.
 * @param firstId
 *   ID of the first file in this page of results.
 * @param lastId
 *   ID of the last file in this page of results.
 * @param hasMore
 *   Whether there are more results available.
 */
case class FileListResponse(
  data: Seq[FileMetadata],
  firstId: Option[String],
  lastId: Option[String],
  hasMore: Boolean = false
)

/**
 * Response from deleting a file.
 *
 * @param id
 *   ID of the deleted file.
 */
case class FileDeleteResponse(
  id: String
) extends HasType {
  val `type`: String = "file_deleted"
}
