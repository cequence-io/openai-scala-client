package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.wsclient.domain.EnumValue

/**
 * A single request within a provider-agnostic chat-completion batch.
 *
 * @param customId
 *   Developer-provided ID for matching results to requests - results may be returned out of
 *   request order. Must be unique within the batch. To stay compatible with every provider,
 *   use 1-63 lowercase characters from `[a-z0-9_-]` (Vertex AI restricts custom ids to GCP
 *   label-value constraints; most other providers also accept uppercase and 64 chars).
 * @param messages
 *   The messages of the wrapped chat-completion request.
 */
final case class ChatCompletionBatchRequest(
  customId: String,
  messages: Seq[BaseMessage]
)

/**
 * Provider-agnostic status of a chat-completion batch. The raw provider status is available at
 * [[ChatCompletionBatchInfo.providerStatus]].
 */
sealed trait ChatCompletionBatchStatus extends EnumValue

object ChatCompletionBatchStatus {

  /** The batch is validating, queued, running, finalizing, or cancelling. */
  case object InProgress extends ChatCompletionBatchStatus

  /**
   * Processing ended and results are available (individual requests may still have errored).
   */
  case object Completed extends ChatCompletionBatchStatus

  /** The batch failed as a whole. */
  case object Failed extends ChatCompletionBatchStatus

  /** The batch was cancelled (it may contain partial results). */
  case object Cancelled extends ChatCompletionBatchStatus

  /** The batch expired before all requests were processed. */
  case object Expired extends ChatCompletionBatchStatus

  def values: Seq[ChatCompletionBatchStatus] =
    Seq(InProgress, Completed, Failed, Cancelled, Expired)
}

/**
 * Provider-agnostic handle/status of a chat-completion batch.
 *
 * @param id
 *   Batch id in the provider's format (e.g. `batch_...`, `msgbatch_...`, `batches/...`, or a
 *   Vertex job resource name). Pass it back to the get/results/cancel/delete methods verbatim.
 * @param status
 *   Unified status.
 * @param providerStatus
 *   The provider's raw status string (e.g. `in_progress`, `ended`, `BATCH_STATE_SUCCEEDED`,
 *   `JOB_STATE_RUNNING`).
 */
final case class ChatCompletionBatchInfo(
  id: String,
  status: ChatCompletionBatchStatus,
  providerStatus: String
) {

  /** True once the batch reached a terminal status (successfully or not). */
  def isDone: Boolean = status != ChatCompletionBatchStatus.InProgress
}

/** Error of a single request within a chat-completion batch. */
final case class ChatCompletionBatchError(
  message: String,
  code: Option[String] = None
)

/**
 * Result of a single request within a chat-completion batch: either a chat-completion response
 * or an error (including cancellations/expirations of individual requests).
 */
final case class ChatCompletionBatchResultItem(
  customId: String,
  result: Either[ChatCompletionBatchError, ChatCompletionResponse]
) {
  def responseOption: Option[ChatCompletionResponse] = result.toOption
  def errorOption: Option[ChatCompletionBatchError] = result.left.toOption
}

/**
 * Typed result of a single request within a chat-completion batch: either the JSON-parsed
 * value of type `T` paired with the raw response (kept for usage/metadata), or an error - a
 * provider-side one passed through, or a client-side `response_parse_error` /
 * `missing_result`. Produced by `OpenAIChatCompletionExtra.createChatCompletionBatchWithJSON`.
 */
final case class ChatCompletionBatchTypedResultItem[T](
  customId: String,
  result: Either[ChatCompletionBatchError, (T, ChatCompletionResponse)]
) {
  def valueOption: Option[T] = result.toOption.map(_._1)
  def errorOption: Option[ChatCompletionBatchError] = result.left.toOption
}
