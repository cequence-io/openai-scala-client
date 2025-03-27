package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.responsesapi.{Input, ModelStatus, Output}

/**
 * The results of a file search tool call.
 *
 * @param id
 *   The unique ID of the file search tool call
 * @param queries
 *   The queries used to search for files
 * @param status
 *   The status of the file search tool call
 * @param results
 *   The results of the file search tool call
 */
final case class FileSearchToolCall(
  id: String,
  queries: Seq[String] = Nil,
  status: ModelStatus, // in_progress, searching, incomplete or failed
  results: Seq[FileSearchResult] = Nil
) extends ToolCall
    with Input
    with Output {
  override val `type`: String = "file_search_call"
}

/**
 * Represents a result from a file search tool call.
 *
 * @param attributes
 *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
 *   storing additional information about the object in a structured format.
 * @param fileId
 *   The unique ID of the file.
 * @param filename
 *   The name of the file.
 * @param score
 *   The relevance score of the file - a value between 0 and 1.
 * @param text
 *   The text that was retrieved from the file.
 */
final case class FileSearchResult(
  attributes: Map[String, Any] = Map.empty,
  fileId: Option[String] = None,
  filename: Option[String] = None,
  score: Option[Double] = None,
  text: Option[String] = None
)
