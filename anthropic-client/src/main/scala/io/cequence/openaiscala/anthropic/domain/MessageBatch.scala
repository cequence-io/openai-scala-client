package io.cequence.openaiscala.anthropic.domain

import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.domain.HasType
import io.cequence.wsclient.domain.EnumValue

/**
 * Processing status of a Message Batch. Starts as `in_progress`, then moves to `ended` once
 * all requests have finished processing; `canceling` while a cancellation is being finalized
 * (canceled batches also end up as `ended`).
 */
sealed trait MessageBatchProcessingStatus extends EnumValue

object MessageBatchProcessingStatus {
  case object in_progress extends MessageBatchProcessingStatus
  case object canceling extends MessageBatchProcessingStatus
  case object ended extends MessageBatchProcessingStatus

  def values: Seq[MessageBatchProcessingStatus] = Seq(in_progress, canceling, ended)
}

/**
 * Tallies requests within a Message Batch, categorized by their status. Requests start as
 * `processing` and move to one of the other statuses only once processing of the entire batch
 * ends.
 */
final case class MessageBatchRequestCounts(
  processing: Int,
  succeeded: Int,
  errored: Int,
  canceled: Int,
  expired: Int
)

/**
 * A Message Batch (`/v1/messages/batches`).
 *
 * @param resultsUrl
 *   URL to a `.jsonl` file containing the results. Specified only once processing ends.
 * @see
 *   <a href="https://platform.claude.com/docs/en/build-with-claude/batch-processing">Anthropic
 *   Batch Processing Doc</a>
 */
final case class MessageBatch(
  id: String,
  processingStatus: MessageBatchProcessingStatus,
  requestCounts: MessageBatchRequestCounts,
  createdAt: String,
  expiresAt: String,
  endedAt: Option[String] = None,
  archivedAt: Option[String] = None,
  cancelInitiatedAt: Option[String] = None,
  resultsUrl: Option[String] = None
) extends HasType {
  override val `type`: String = "message_batch"

  def isEnded: Boolean = processingStatus == MessageBatchProcessingStatus.ended
}

/**
 * A single request within a Message Batch.
 *
 * @param customId
 *   Developer-provided ID for matching results to requests (results may come out of order).
 *   Must be unique within the batch; 1-64 chars matching `^[a-zA-Z0-9_-]{1,64}$`.
 * @param messages
 *   The messages of the wrapped Messages API request.
 * @param settings
 *   The settings of the wrapped Messages API request.
 */
final case class MessageBatchRequest(
  customId: String,
  messages: Seq[Message],
  settings: AnthropicCreateMessageSettings
)

/** Response envelope of the list-batches endpoint (`GET /v1/messages/batches`). */
final case class ListMessageBatchesResponse(
  data: Seq[MessageBatch],
  hasMore: Boolean,
  firstId: Option[String] = None,
  lastId: Option[String] = None
)

/** Error of a single errored request within a Message Batch. */
final case class MessageBatchError(
  `type`: String,
  message: String
)

/**
 * Processing result of a single request in a Message Batch: a Message output if successful, an
 * error response if processing failed, or the reason why processing was not attempted
 * (cancellation or expiration).
 */
sealed trait MessageBatchResult {
  val `type`: String
}

object MessageBatchResult {

  final case class Succeeded(message: CreateMessageResponse) extends MessageBatchResult {
    override val `type`: String = "succeeded"
  }

  final case class Errored(
    error: MessageBatchError,
    requestId: Option[String] = None
  ) extends MessageBatchResult {
    override val `type`: String = "errored"
  }

  case object Canceled extends MessageBatchResult {
    override val `type`: String = "canceled"
  }

  case object Expired extends MessageBatchResult {
    override val `type`: String = "expired"
  }
}

/**
 * One line of a Message Batch results `.jsonl` file. Results are not guaranteed to be in the
 * same order as requests - use [[customId]] to match results to requests.
 */
final case class MessageBatchIndividualResponse(
  customId: String,
  result: MessageBatchResult
)

/** Response from deleting a Message Batch. */
final case class MessageBatchDeleteResponse(id: String) extends HasType {
  override val `type`: String = "message_batch_deleted"
}
