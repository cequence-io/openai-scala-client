package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

/**
 * Provider-agnostic chat-completion batch processing (~50% of standard cost, async, typically
 * a 24h turnaround target).
 *
 * This is an '''opt-in capability trait''', deliberately '''not''' part of the base
 * [[OpenAIChatCompletionService]]: only services that actually support batch mix it in - the
 * full OpenAI service ([[OpenAIService]], via the Batch API) and the Anthropic (Message
 * Batches), Bedrock (batch inference), Gemini (Batch Mode), and Vertex AI (batch prediction)
 * adapters. Its methods are abstract (no default), so a new batch-capable service is forced by
 * the compiler to implement them rather than silently inheriting a "not supported" stub. To
 * take a batch-capable service through this API, hold a `OpenAIChatCompletionService with
 * OpenAIChatCompletionBatchService` reference (returned directly by the provider factories),
 * or route across providers with `OpenAIServiceAdapters.chatCompletionBatchRouter`.
 *
 * [[getChatCompletionBatch]], [[retrieveChatCompletionBatchResults]],
 * [[cancelChatCompletionBatch]], and [[deleteChatCompletionBatch]] all take the `model` the
 * batch was created with, alongside its id. A batch id alone is not a routing key - it is an
 * opaque, provider-specific string - so when several provider services are composed behind one
 * batch router (`OpenAIServiceAdapters.chatCompletionBatchRouter`), `model` is what lets the
 * call be dispatched to the right one deterministically, the same way `settings.model` already
 * dispatches `createChatCompletion`.
 */
trait OpenAIChatCompletionBatchService extends OpenAIServiceConsts {

  /**
   * Submits a batch of chat-completion requests for asynchronous processing.
   *
   * Poll [[getChatCompletionBatch]] until [[ChatCompletionBatchInfo.isDone]], then fetch
   * [[retrieveChatCompletionBatchResults]] - or use
   * `OpenAIChatCompletionExtra.createChatCompletionBatchAndWaitForResults` for the whole flow
   * in one call.
   *
   * @param requests
   *   Requests to batch, each with a unique `customId` and its messages.
   * @param settings
   *   Chat-completion settings (model, temperature, ...) shared by all requests in the batch.
   * @return
   *   the created batch handle (status `InProgress`)
   */
  def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Future[ChatCompletionBatchInfo]

  /**
   * Retrieves the current status of a chat-completion batch.
   *
   * @param batchId
   *   The id from [[createChatCompletionBatch]] (provider-specific format).
   * @param model
   *   The model the batch was created with - see the trait scaladoc for why this is needed.
   */
  def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo]

  /**
   * Retrieves the results of a finished chat-completion batch. Results are not guaranteed to
   * be in request order - match by `customId`. Each item carries either a
   * [[io.cequence.openaiscala.domain.response.ChatCompletionResponse]] or an error (including
   * per-request cancellations/expirations).
   *
   * @param batchId
   *   The id from [[createChatCompletionBatch]] (provider-specific format).
   * @param model
   *   The model the batch was created with - see the trait scaladoc for why this is needed.
   */
  def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]]

  /**
   * Cancels an in-progress chat-completion batch. Cancellation may take a moment to finalize -
   * poll [[getChatCompletionBatch]] until done; the batch may contain partial results.
   *
   * @param batchId
   *   The id from [[createChatCompletionBatch]] (provider-specific format).
   * @param model
   *   The model the batch was created with - see the trait scaladoc for why this is needed.
   */
  def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo]

  /**
   * Deletes a finished chat-completion batch including all the files it created or staged
   * (cancel an in-progress batch first). Per provider: OpenAI - deletes the batch's
   * input/output/error files (the Batch API has no delete endpoint, so the batch object itself
   * remains listed); Anthropic - deletes the batch and with it its results; Gemini - deletes
   * the batch plus, for file-based batches, the staged input file and the generated results
   * file; Vertex AI - deletes the job plus the staged Cloud Storage input/output objects.
   *
   * @param batchId
   *   The id from [[createChatCompletionBatch]] (provider-specific format).
   * @param model
   *   The model the batch was created with - see the trait scaladoc for why this is needed.
   */
  def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit]
}
