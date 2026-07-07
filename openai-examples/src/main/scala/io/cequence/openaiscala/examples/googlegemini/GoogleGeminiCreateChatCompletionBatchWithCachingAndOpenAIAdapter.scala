package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.response.GenerateContentResponse
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Batch processing and context caching used together on the Gemini Batch API, everything
 * through the OpenAI adapter. Gemini's implicit caching does not apply to Batch Mode, so the
 * working route is '''explicit''' caching:
 *
 *   1. A first regular chat completion with `enableCacheSystemMessage(true)` makes the adapter
 *      store the (large) system message as a `CachedContent` resource; its name is read from
 *      the response.
 *   1. Both batches then reference the cache via `setSystemCacheName(cacheName)` - the adapter
 *      drops the per-request system message and attaches the cache reference to every batched
 *      request instead.
 *
 * The batch runs at 50% of standard cost and the cached prefix tokens are billed at a reduced
 * rate on every request (plus an hourly storage fee while the cache lives). Cache hits are
 * reported per result via `usage.prompt_tokens_details.cached_tokens` (mapped from Gemini's
 * `cachedContentTokenCount`).
 *
 * Notes: the cached content must meet the model's minimum cacheable size (1024 tokens for
 * Gemini 2.5 Flash, 2048 for 2.5 Pro) and is pinned to one model. The adapter-created cache
 * has a 5-minute TTL, so the batches are submitted concurrently right after it is created -
 * for production batches create a cache with a longer TTL.
 *
 * Requires `GOOGLE_API_KEY`.
 */
object GoogleGeminiCreateChatCompletionBatchWithCachingAndOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService with OpenAIChatCompletionBatchService] {

  override val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    GeminiServiceFactory.asOpenAI()

  private val model = NonOpenAIModelId.gemini_2_5_flash

  // ~5k tokens of deterministic shared context - well above Gemini 2.5 Flash's 1024-token
  // minimum cacheable size
  private val sharedContext = {
    val paragraph =
      "The Kingdom of Norway is a Nordic country in Northern Europe situated on the " +
        "Scandinavian Peninsula, known for its deep fjords carved by ancient glaciers, its " +
        "extensive coastline along the North Atlantic Ocean, a strong maritime tradition " +
        "dating back to the Viking Age, substantial petroleum and hydropower resources, and " +
        "a constitutional monarchy with a parliamentary system of governance. "

    (1 to 60).map(index => s"Fact block $index: $paragraph").mkString("\n")
  }

  private val systemMessage = SystemMessage(
    "You are a concise assistant. The following background context may or may not be " +
      s"relevant - answer from general knowledge when it is not.\n\n$sharedContext"
  )

  private def requests(batchTag: String) = Seq("Norway", "Sweden", "Denmark").map { country =>
    ChatCompletionBatchRequest(
      customId = s"${country.toLowerCase}-$batchTag",
      // the system message is passed too - the adapter swaps it for the cache reference
      messages = Seq(
        systemMessage,
        UserMessage(s"What is the capital of $country? Reply in one word.")
      )
    )
  }

  override protected def run: Future[_] =
    for {
      // First call: enable caching - the adapter stores the system message as a CachedContent
      // resource and the response carries its name.
      response1 <- service.createChatCompletion(
        messages =
          Seq(systemMessage, UserMessage("What is the capital of Finland? One word.")),
        settings = CreateChatCompletionSettings(model).enableCacheSystemMessage(true)
      )
      cacheName = getCacheName(response1)
      _ = println(s"call 1 (cache created): '${response1.contentHead}' cache=$cacheName")

      // Both batches reuse the cache by name. Submitted concurrently so they complete within
      // the cache's 5-minute TTL.
      settings = CreateChatCompletionSettings(model).setSystemCacheName(cacheName)

      batchA = service.createChatCompletionBatchAndWaitForResults(
        requests("batch-a"),
        settings,
        pollingInterval = 10.seconds,
        deleteBatchAfterUse = true
      )
      batchB = service.createChatCompletionBatchAndWaitForResults(
        requests("batch-b"),
        settings,
        pollingInterval = 10.seconds,
        deleteBatchAfterUse = true
      )

      resultsA <- batchA
      _ = report("batch A", resultsA)

      resultsB <- batchB
      _ = report("batch B", resultsB)
    } yield ()

  private def getCacheName(response: ChatCompletionResponse): String =
    response.originalResponse
      .getOrElse(throw new IllegalStateException("Original response not found"))
      .asInstanceOf[GenerateContentResponse]
      .cachedContent
      .getOrElse(throw new IllegalStateException("Cached content not found"))

  private def report(
    label: String,
    results: Seq[ChatCompletionBatchResultItem]
  ): Unit = {
    println(s"--- $label ---")
    results.foreach { item =>
      item.result match {
        case Right(response) =>
          val promptTokens = response.usage.map(_.prompt_tokens).getOrElse(0)
          val cachedTokens =
            response.usage.flatMap(_.prompt_tokens_details).map(_.cached_tokens).getOrElse(0)
          println(
            s"${item.customId}: '${response.contentHead}' promptTokens=$promptTokens cachedTokens=$cachedTokens"
          )
        case Left(error) =>
          println(s"${item.customId}: ERROR ${error.message}")
      }
    }
  }
}
