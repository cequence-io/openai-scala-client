package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Batch processing and prompt caching used together on the Anthropic Message Batches API,
 * through the OpenAI adapter - the two discounts stack: 50% off for the batch, plus ~90% off
 * the (shared) cached prefix on every cache hit.
 *
 * How it works: every request in the batch shares a large system prompt, and
 * `setUseAnthropicSystemMessagesCache(true)` puts an identical `cache_control` block on it in
 * every batched request - exactly what Anthropic recommends for batches. Since batch requests
 * are processed concurrently, cache hits are best-effort (docs: 30-98%): within the first
 * batch typically only some requests hit (whoever runs first writes the cache), while a
 * follow-up batch sent within the cache TTL hits on ~all requests. For batches that take
 * longer than the default 5-minute TTL, consider the 1-hour cache duration.
 *
 * Note: the shared prefix must meet the model's minimum cacheable size (4096 tokens for Claude
 * Haiku 4.5; 2048 for Fable 5 / Sonnet 4.6) - shorter prefixes silently won't cache. Cache
 * hits are reported per result via `usage.prompt_tokens_details.cached_tokens`.
 *
 * Requires `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateChatCompletionBatchWithCachingAndOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService with OpenAIChatCompletionBatchService] {

  override val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    AnthropicServiceFactory.asOpenAI()

  private val settings =
    CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_4_5)
      // put an identical cache_control block on the system message of every batched request
      .setUseAnthropicSystemMessagesCache(true)

  // ~5k tokens of deterministic shared context - above Haiku 4.5's 4096-token minimum
  // cacheable prefix
  private val sharedContext = {
    val paragraph =
      "The Kingdom of Norway is a Nordic country in Northern Europe situated on the " +
        "Scandinavian Peninsula, known for its deep fjords carved by ancient glaciers, its " +
        "extensive coastline along the North Atlantic Ocean, a strong maritime tradition " +
        "dating back to the Viking Age, substantial petroleum and hydropower resources, and " +
        "a constitutional monarchy with a parliamentary system of governance. "

    (1 to 60).map(index => s"Fact block $index: $paragraph").mkString("\n")
  }

  private def requests(batchTag: String) = Seq("Norway", "Sweden", "Denmark").map { country =>
    ChatCompletionBatchRequest(
      customId = s"${country.toLowerCase}-$batchTag",
      messages = Seq(
        SystemMessage(
          "You are a concise assistant. The following background context may or may not be " +
            s"relevant - answer from general knowledge when it is not.\n\n$sharedContext"
        ),
        UserMessage(s"What is the capital of $country? Reply in one word.")
      )
    )
  }

  override protected def run: Future[_] =
    for {
      // first batch: whoever runs first writes the cache; later requests may already hit it
      resultsA <- service.createChatCompletionBatchAndWaitForResults(
        requests("batch-a"),
        settings,
        pollingInterval = 10.seconds,
        deleteBatchAfterUse = true
      )
      _ = report("batch A (cache cold)", resultsA)

      // second batch within the cache TTL: expect hits on ~all requests
      resultsB <- service.createChatCompletionBatchAndWaitForResults(
        requests("batch-b"),
        settings,
        pollingInterval = 10.seconds,
        deleteBatchAfterUse = true
      )
      _ = report("batch B (cache warm)", resultsB)
    } yield ()

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
