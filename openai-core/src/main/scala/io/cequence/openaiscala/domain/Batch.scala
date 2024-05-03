package io.cequence.openaiscala.domain

object Batch {

  sealed abstract class BatchEndpoint extends EnumValue
  object BatchEndpoint {
    case object `/v1/chat/completions` extends BatchEndpoint
    case object `/v1/embeddings` extends BatchEndpoint
  }

  sealed trait CompletionWindow extends EnumValue
  object CompletionWindow {
    case object `24h` extends CompletionWindow
  }

  /**
   * Represents a Batch object returned by the OpenAI API.
   *
   * @param id
   *   The ID of the batch.
   * @param object
   *   The object type, which is always "batch".
   * @param endpoint
   *   The OpenAI API endpoint used by the batch.
   * @param errors
   *   Map of errors, if any occurred during the batch processing.
   * @param input_file_id
   *   The ID of the input file for the batch.
   * @param completion_window
   *   The time frame within which the batch should be processed.
   * @param status
   *   The current status of the batch.
   * @param output_file_id
   *   The ID of the file containing the outputs of successfully executed requests, if
   *   available.
   * @param error_file_id
   *   The ID of the file containing the outputs of requests with errors, if available.
   * @param created_at
   *   The Unix timestamp (in seconds) for when the batch was created.
   * @param in_progress_at
   *   The Unix timestamp (in seconds) for when the batch started processing, if available.
   * @param expires_at
   *   The Unix timestamp (in seconds) for when the batch will expire, if available.
   * @param finalizing_at
   *   The Unix timestamp (in seconds) for when the batch started finalizing, if available.
   * @param completed_at
   *   The Unix timestamp (in seconds) for when the batch was completed, if available.
   * @param failed_at
   *   The Unix timestamp (in seconds) for when the batch failed, if available.
   * @param expired_at
   *   The Unix timestamp (in seconds) for when the batch expired, if available.
   * @param cancelling_at
   *   The Unix timestamp (in seconds) for when the batch started cancelling, if available.
   * @param cancelled_at
   *   The Unix timestamp (in seconds) for when the batch was cancelled, if available.
   * @param request_counts
   *   Map detailing the counts of requests by their status within the batch.
   * @param metadata
   *   Optional metadata as a map. This can be useful for storing additional information about
   *   the batch in a structured format.
   */
  case class Batch(
    id: String,
    `object`: String,
    endpoint: BatchEndpoint,
    errors: Option[Map[String, String]],
    input_file_id: String,
    completion_window: CompletionWindow,
    status: String,
    output_file_id: Option[String],
    error_file_id: Option[String],
    created_at: Long,
    in_progress_at: Option[Long],
    expires_at: Option[Long],
    finalizing_at: Option[Long],
    completed_at: Option[Long],
    failed_at: Option[Long],
    expired_at: Option[Long],
    cancelling_at: Option[Long],
    cancelled_at: Option[Long],
    request_counts: Map[String, Int],
    metadata: Option[Map[String, String]]
  )
}
