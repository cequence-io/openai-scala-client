package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.EnumValue

object Batch {

  sealed abstract class BatchEndpoint extends EnumValue
  object BatchEndpoint {
    case object `/v1/chat/completions` extends BatchEndpoint {
      override def toString: String = "/v1/chat/completions"
    }
    case object `/v1/embeddings` extends BatchEndpoint {
      override def toString: String = "/v1/embeddings"
    }
  }

  sealed abstract class CompletionWindow extends EnumValue
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
    errors: Option[BatchProcessingErrors],
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
  ) {
    def isRunning: Boolean =
      List("in_progress", "validating", "finalizing", "cancelling").contains(status)

    // "failed", "completed", "expired", "cancelled"
    def isFinished: Boolean = !isRunning

    def isSuccess: Boolean = status == "completed"

    def isFailedOrCancelledOrExpired: Boolean = isFinished && !isSuccess
  }

  case class BatchProcessingErrors(
    `object`: String,
//    data: Map[String, String]
    data: Seq[BatchProcessingError]
  )

  case class BatchProcessingError(
    code: String,
    message: String,
    param: Option[String],
    line: Option[Int]
  )

  /**
   * Represents a request in a batch. The per-line object of the batch input file
   *
   * @param custom_id
   *   A developer-provided per-request id that will be used to match outputs to inputs. Must
   *   be unique for each request in a batch.
   * @param method
   *   The HTTP method to be used for the request. Currently only POST is supported.
   * @param url
   *   The OpenAI API relative URL to be used for the request. Currently /v1/chat/completions
   *   and /v1/embeddings are supported.
   * @param body
   *   The per-line object of the batch input file.
   */
  case class BatchRow(
    custom_id: String,
    method: String,
    url: String,
    body: Map[String, Seq[BaseMessage]]
  )

  object BatchRow {
    def buildBatchRows(
      model: String,
      requests: Seq[BatchRowBase]
    ): Seq[BatchRow] =
      requests.map(_.toBatchInput(model))
  }

  /**
   * Simplification of the [[BatchRow]] as method is always POST and the model should be the
   * same for all the requests in one batch.
   *
   * @param custom_id
   *   A developer-provided per-request id that will be used to match outputs to inputs. Must
   *   be unique for each request in a batch.
   * @param url
   *   The OpenAI API relative URL to be used for the request. Currently /v1/chat/completions
   *   and /v1/embeddings are supported.
   * @param messages
   *   Messages for either chat completions or embeddings.
   */
  case class BatchRowBase(
    custom_id: String,
    url: String,
    messages: Seq[BaseMessage]
  ) {
    def toBatchInput(model: String): BatchRow =
      BatchRow(custom_id, "POST", url, Map(model -> messages))
  }
}
