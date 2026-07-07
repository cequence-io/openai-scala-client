package io.cequence.openaiscala.gemini.domain

import io.cequence.openaiscala.gemini.domain.response.GenerateContentResponse
import io.cequence.wsclient.domain.EnumValue

/**
 * State of a Gemini batch job.
 *
 * @see
 *   <a href="https://ai.google.dev/api/batch-mode">Gemini Batch Mode Docs</a>
 */
sealed trait BatchState extends EnumValue

object BatchState {
  case object BATCH_STATE_UNSPECIFIED extends BatchState
  case object BATCH_STATE_PENDING extends BatchState
  case object BATCH_STATE_RUNNING extends BatchState
  case object BATCH_STATE_SUCCEEDED extends BatchState
  case object BATCH_STATE_FAILED extends BatchState
  case object BATCH_STATE_CANCELLED extends BatchState
  case object BATCH_STATE_EXPIRED extends BatchState

  def values: Seq[BatchState] = Seq(
    BATCH_STATE_UNSPECIFIED,
    BATCH_STATE_PENDING,
    BATCH_STATE_RUNNING,
    BATCH_STATE_SUCCEEDED,
    BATCH_STATE_FAILED,
    BATCH_STATE_CANCELLED,
    BATCH_STATE_EXPIRED
  )

  /** Terminal states - the batch will not change state anymore. */
  def terminalValues: Seq[BatchState] = Seq(
    BATCH_STATE_SUCCEEDED,
    BATCH_STATE_FAILED,
    BATCH_STATE_CANCELLED,
    BATCH_STATE_EXPIRED
  )
}

/** Request counts of a batch, by status. */
final case class BatchStats(
  requestCount: Option[Long] = None,
  successfulRequestCount: Option[Long] = None,
  failedRequestCount: Option[Long] = None,
  pendingRequestCount: Option[Long] = None
)

/** A `google.rpc.Status` error attached to a batch or one of its responses. */
final case class BatchRpcError(
  code: Option[Int] = None,
  message: Option[String] = None,
  status: Option[String] = None
)

/**
 * The response (or error) of a single inlined batch request.
 *
 * @param key
 *   The `metadata.key` echoed from the corresponding request, for matching.
 */
final case class BatchInlinedResponse(
  key: Option[String] = None,
  response: Option[GenerateContentResponse] = None,
  error: Option[BatchRpcError] = None
)

/**
 * Output of a batch: either a results file name (file-based input) or inlined responses
 * (inline input).
 */
final case class BatchOutput(
  responsesFile: Option[String] = None,
  inlinedResponses: Seq[BatchInlinedResponse] = Nil
)

/**
 * A single inline request of a batch.
 *
 * @param key
 *   Developer-provided key (sent as `metadata.key`) for matching responses to requests -
 *   responses may come out of request order.
 * @param contents
 *   The content of the wrapped `generateContent` request.
 * @param systemInstruction
 *   Optional per-request system instruction. Each inlined batch request is a full
 *   `generateContent` request, so this overrides the batch-wide (settings-level)
 *   `systemInstruction` for this request only - preventing one request's system prompt from
 *   leaking into another's. When `None`, the batch-wide `systemInstruction` (e.g. from
 *   explicit caching) applies unchanged.
 */
final case class BatchRequestItem(
  key: String,
  contents: Seq[Content],
  systemInstruction: Option[Content] = None
)

/**
 * A Gemini batch job (`batchGenerateContent` / `batches/{batchId}`), processed at 50% of
 * standard cost with a 24h turnaround target.
 *
 * @param name
 *   Resource name, format `batches/{batchId}`.
 * @param done
 *   Whether the underlying long-running operation has completed (regardless of outcome).
 * @param output
 *   Populated once the batch succeeds - inlined responses for inline input, or a results file
 *   name for file input.
 * @param inputFileName
 *   The input file (`files/{id}`) the batch reads from - set only for file-based input.
 * @see
 *   <a href="https://ai.google.dev/api/batch-mode">Gemini Batch Mode Docs</a>
 */
final case class GenerateContentBatch(
  name: String,
  displayName: Option[String] = None,
  model: Option[String] = None,
  state: Option[BatchState] = None,
  createTime: Option[String] = None,
  updateTime: Option[String] = None,
  endTime: Option[String] = None,
  priority: Option[Long] = None,
  batchStats: Option[BatchStats] = None,
  output: Option[BatchOutput] = None,
  inputFileName: Option[String] = None,
  done: Option[Boolean] = None,
  error: Option[BatchRpcError] = None
) {
  def isTerminated: Boolean = state.exists(BatchState.terminalValues.contains)
}

/** Response envelope of the list-batches endpoint (`GET /v1beta/batches`). */
final case class ListBatchesResponse(
  batches: Seq[GenerateContentBatch],
  nextPageToken: Option[String] = None
)
