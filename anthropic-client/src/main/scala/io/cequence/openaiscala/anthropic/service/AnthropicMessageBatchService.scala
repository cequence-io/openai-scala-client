package io.cequence.openaiscala.anthropic.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.{
  ListMessageBatchesResponse,
  MessageBatch,
  MessageBatchDeleteResponse,
  MessageBatchIndividualResponse,
  MessageBatchRequest
}

import scala.concurrent.Future

/**
 * Anthropic Message Batches API (`/v1/messages/batches`) — asynchronous processing of large
 * volumes of Messages requests at 50% of standard cost. A batch is limited to 100,000 requests
 * or 256 MB (whichever is reached first), takes up to 24 hours to process (most finish within
 * an hour), and its results are available for 29 days after creation. No beta header required.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/build-with-claude/batch-processing">Anthropic
 *   Batch Processing Doc</a>
 */
trait AnthropicMessageBatchService {

  /**
   * Creates a Message Batch (`POST /v1/messages/batches`). Processing begins immediately.
   *
   * Note that validation of each request's params is performed asynchronously - validation
   * errors surface as `errored` results once processing of the entire batch has ended.
   *
   * @param requests
   *   Requests to batch, each with a unique `customId` and standard Messages API
   *   messages/settings.
   * @return
   *   the created batch (processing status `in_progress`)
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/creating-message-batches">Anthropic
   *   Doc</a>
   */
  def createMessageBatch(
    requests: Seq[MessageBatchRequest]
  ): Future[MessageBatch]

  /**
   * Retrieves a Message Batch (`GET /v1/messages/batches/{id}`). Poll this until
   * `processingStatus` is `ended`, then fetch results.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/retrieving-message-batches">Anthropic
   *   Doc</a>
   */
  def getMessageBatch(batchId: String): Future[MessageBatch]

  /**
   * Lists Message Batches within the workspace (`GET /v1/messages/batches`), most recently
   * created first.
   *
   * @param limit
   *   Items per page (default 20, range 1-1000).
   * @param beforeId
   *   Cursor - returns the page of results immediately before this object.
   * @param afterId
   *   Cursor - returns the page of results immediately after this object.
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/listing-message-batches">Anthropic
   *   Doc</a>
   */
  def listMessageBatches(
    limit: Option[Int] = None,
    beforeId: Option[String] = None,
    afterId: Option[String] = None
  ): Future[ListMessageBatchesResponse]

  /**
   * Streams the results of an ended Message Batch (`GET /v1/messages/batches/{id}/results`) as
   * they are read from the results `.jsonl` file - recommended for large batches. Results are
   * not guaranteed to be in request order; match by `customId`.
   *
   * @see
   *   <a
   *   href="https://platform.claude.com/docs/en/api/retrieving-message-batch-results">Anthropic
   *   Doc</a>
   */
  def streamMessageBatchResults(
    batchId: String
  ): Source[MessageBatchIndividualResponse, NotUsed]

  /**
   * Retrieves all results of an ended Message Batch in one buffered response. For very large
   * batches prefer [[streamMessageBatchResults]], which streams the results file incrementally
   * instead of buffering it whole.
   *
   * @see
   *   <a
   *   href="https://platform.claude.com/docs/en/api/retrieving-message-batch-results">Anthropic
   *   Doc</a>
   */
  def retrieveMessageBatchResults(
    batchId: String
  ): Future[Seq[MessageBatchIndividualResponse]]

  /**
   * Cancels a Message Batch (`POST /v1/messages/batches/{id}/cancel`). Immediately after,
   * `processingStatus` is `canceling`; poll until `ended` - the batch may contain partial
   * results for requests processed before cancellation.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/canceling-message-batches">Anthropic
   *   Doc</a>
   */
  def cancelMessageBatch(batchId: String): Future[MessageBatch]

  /**
   * Deletes a Message Batch (`DELETE /v1/messages/batches/{id}`). Batches can only be deleted
   * once they've finished processing - cancel an in-progress batch first.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/deleting-message-batches">Anthropic
   *   Doc</a>
   */
  def deleteMessageBatch(batchId: String): Future[MessageBatchDeleteResponse]
}
