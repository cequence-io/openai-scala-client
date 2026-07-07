package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  ChatCompletionTool
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Wraps a plain [[OpenAIChatCompletionService]] (one without native batch support) and exposes
 * [[OpenAIChatCompletionBatchService]] by '''emulating''' a batch: each
 * `createChatCompletionBatch` logs a warning and runs its requests as ordinary synchronous
 * chat completions (up to `maxParallelism` concurrently), holding the results in memory keyed
 * by a synthetic batch id.
 *
 * This makes any chat-completion service usable where a batch-capable one is required -
 * notably as a member of `OpenAIServiceAdapters.chatCompletionBatchRouter`, so a router can
 * mix natively-batching providers with fallback ones.
 *
 * Caveats (inherent to emulation, hence the warning): there is no real ~50% batch discount and
 * no async/24h processing - the requests run immediately at standard cost; and results live
 * only in '''this''' instance's memory, so the submit/poll/retrieve split-flow only works
 * within one process/instance lifetime (a fresh instance cannot see a previous instance's
 * emulated batch). Results are retained until `deleteChatCompletionBatch` /
 * `cancelChatCompletionBatch` is called for them, or until they are evicted on a FIFO basis
 * once more than `maxRetainedBatches` emulated batches exist at the same time - so a
 * retrieve/get is non-consuming (repeatable), matching the behavior of native providers, while
 * still bounding memory on the common submit->poll->retrieve flow whose helper defaults to
 * `deleteBatchAfterUse = false`.
 */
private class ChatCompletionBatchEmulationAdapter(
  underlying: OpenAIChatCompletionService,
  warn: String => Unit,
  maxParallelism: Int,
  maxRetainedBatches: Int
)(
  implicit ec: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionBatchService {

  private val emulatedProviderStatus = "emulated"

  // insertion-ordered, size-bounded store: once more than maxRetainedBatches entries are
  // present, the oldest one is evicted automatically. All access is synchronized on `results`
  // since LinkedHashMap isn't thread-safe - fine for emulation given the low contention.
  private val results =
    new java.util.LinkedHashMap[String, Future[Seq[ChatCompletionBatchResultItem]]]() {
      override def removeEldestEntry(
        e: java.util.Map.Entry[String, Future[Seq[ChatCompletionBatchResultItem]]]
      ): Boolean = size > maxRetainedBatches
    }

  private def putResult(
    batchId: String,
    resultsFuture: Future[Seq[ChatCompletionBatchResultItem]]
  ): Unit =
    results.synchronized {
      results.put(batchId, resultsFuture)
      ()
    }

  private def getResult(batchId: String): Option[Future[Seq[ChatCompletionBatchResultItem]]] =
    results.synchronized(Option(results.get(batchId)))

  private def removeResult(batchId: String): Unit =
    results.synchronized {
      results.remove(batchId)
      ()
    }

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = underlying.createChatCompletion(messages, settings)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    underlying.createChatToolCompletion(messages, tools, responseToolChoice, settings)

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] = {
    warn(
      s"Batch is not natively supported for model '${settings.model}' - falling back to " +
        s"${requests.size} synchronous chat completion(s), running up to $maxParallelism " +
        "concurrently (no batch discount, results kept in memory)."
    )

    val batchId = s"emulated-batch-${UUID.randomUUID()}"

    // bounded fan-out: process requests in maxParallelism-sized chunks, sequentially chunk by
    // chunk, while still running each chunk's requests concurrently. Order is preserved.
    val resultsFuture = requests
      .grouped(maxParallelism)
      .foldLeft(Future.successful(Seq.empty[ChatCompletionBatchResultItem])) {
        case (accF, chunk) =>
          accF.flatMap { acc =>
            Future.sequence(chunk.map(runOne(_, settings))).map(acc ++ _)
          }
      }

    putResult(batchId, resultsFuture)

    Future.successful(
      ChatCompletionBatchInfo(
        batchId,
        ChatCompletionBatchStatus.InProgress,
        emulatedProviderStatus
      )
    )
  }

  private def runOne(
    request: ChatCompletionBatchRequest,
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchResultItem] =
    underlying
      .createChatCompletion(request.messages, settings)
      .map(response => ChatCompletionBatchResultItem(request.customId, Right(response)))
      .recover { case e: Throwable =>
        ChatCompletionBatchResultItem(
          request.customId,
          Left(ChatCompletionBatchError(e.getMessage))
        )
      }

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    getResult(batchId) match {
      case Some(resultsFuture) =>
        val status =
          if (resultsFuture.isCompleted) ChatCompletionBatchStatus.Completed
          else ChatCompletionBatchStatus.InProgress
        Future.successful(ChatCompletionBatchInfo(batchId, status, emulatedProviderStatus))

      case None =>
        Future.failed(unknownBatch(batchId))
    }

  // retrieval is non-consuming, just like every native provider - see the class scaladoc for
  // how memory is bounded instead (FIFO eviction past maxRetainedBatches).
  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    getResult(batchId).getOrElse(Future.failed(unknownBatch(batchId)))

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] = {
    // the synchronous completions have already been kicked off - nothing to cancel; drop the
    // handle and report a terminal status
    removeResult(batchId)
    Future.successful(
      ChatCompletionBatchInfo(
        batchId,
        ChatCompletionBatchStatus.Cancelled,
        emulatedProviderStatus
      )
    )
  }

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] = {
    removeResult(batchId)
    Future.successful(())
  }

  private def unknownBatch(batchId: String) =
    new OpenAIScalaClientException(
      s"Unknown emulated batch id '$batchId' (emulated batches live only in the creating instance's memory)."
    )

  override def close(): Unit = underlying.close()
}
