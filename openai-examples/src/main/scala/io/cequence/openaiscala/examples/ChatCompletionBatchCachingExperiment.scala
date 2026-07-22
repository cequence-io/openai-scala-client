package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ModelId,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService,
  OpenAIServiceFactory
}
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Experiment: does prompt caching kick in alongside batch processing? All requests share a
 * large (~5k-token) common prefix - above every provider's minimum cacheable size - and two
 * identical batches run back-to-back; cache hits are read from the unified
 * `usage.prompt_tokens_details.cached_tokens` of each result.
 *
 * Findings (July 2026, live):
 *   - Anthropic: caching officially works with batches and the discounts stack. Hits are
 *     best-effort (docs: 30-98%) since batch requests run concurrently - observed 1/3 hits in
 *     the first batch and 3/3 (~99.6% of the prompt) in the second. Requires identical
 *     `cache_control` blocks in every request (enabled here via
 *     `setUseAnthropicSystemMessagesCache`); consider the 1-hour TTL for long batches.
 *   - OpenAI: caching is automatic for prompts >= 1024 tokens on the synchronous API, but the
 *     Batch API reported zero cached tokens in both batches - prompt caching does not apply
 *     (the batch 50% discount stands alone).
 *   - Gemini: implicit caching (2.5+ models) reported zero cached tokens in both batches - it
 *     does not appear to apply to Batch Mode. Explicit caching is the documented route: a
 *     `cachedContent` reference set on `GenerateContentSettings` flows into every batched
 *     request at the `GeminiService.createBatchGenerateContent` level.
 *   - Vertex AI: as Gemini; hits would be reported via `cachedContentTokenCount`, which the
 *     batch adapter maps to `cached_tokens` (requires GCS batch support and GCP credentials).
 *
 * Usage: `runMain ...ChatCompletionBatchCachingExperiment (openai|anthropic|gemini|vertexai)`
 */
object ChatCompletionBatchCachingExperiment {

  // ~5k tokens of deterministic shared context - above every provider's minimum cacheable
  // prefix (OpenAI 1024, Gemini 2.5 ~2048, Anthropic Haiku 4.5 4096)
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

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val system: ActorSystem = ActorSystem("caching-experiment")
    implicit val scheduler: Scheduler = system.scheduler

    val provider = args.headOption.getOrElse("openai")

    val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
      provider match {
        case "anthropic" => AnthropicServiceFactory.asOpenAI()
        case "gemini"    => GeminiServiceFactory.asOpenAI()
        case "vertexai" =>
          VertexAIServiceFactory.asOpenAIWithBatchSupport(
            gcsBucket = sys.env("VERTEXAI_BATCH_GCS_BUCKET")
          )
        case _ => OpenAIServiceFactory()
      }

    val settings = {
      val base = provider match {
        case "anthropic" => CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_4_5)
        case "gemini" | "vertexai" =>
          CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash)
        case _ => CreateChatCompletionSettings(ModelId.gpt_4o_mini)
      }

      // Anthropic caching is opt-in: put a cache_control block on the (shared) system message
      if (provider == "anthropic") base.setUseAnthropicSystemMessagesCache(true) else base
    }

    val flow = for {
      _ <- Future.successful(println(s"provider: $provider, model: ${settings.model}"))

      resultsA <- service.createChatCompletionBatchAndWaitForResults(
        requests("batch-a"),
        settings,
        pollingInterval = 15.seconds,
        deleteBatchAfterUse = true
      )
      _ = report("batch A (cold)", resultsA)

      // the second, identical batch runs right after - entries written by batch A should
      // still be warm (5-min TTLs) if the provider caches across/within batches
      resultsB <- service.createChatCompletionBatchAndWaitForResults(
        requests("batch-b"),
        settings,
        pollingInterval = 15.seconds,
        deleteBatchAfterUse = true
      )
      _ = report("batch B (warm)", resultsB)
    } yield ()

    try Await.result(flow, 60.minutes)
    finally {
      service.close()
      system.terminate()
      ()
    }
  }

  private def report(
    label: String,
    results: Seq[ChatCompletionBatchResultItem]
  ): Unit = {
    println(s"--- $label ---")
    results.foreach { item =>
      item.result match {
        case Right(response) =>
          val prompt = response.usage.map(_.prompt_tokens).getOrElse(0)
          val cached =
            response.usage.flatMap(_.prompt_tokens_details).map(_.cached_tokens).getOrElse(0)
          println(
            s"${item.customId}: '${response.contentHead}' promptTokens=$prompt cachedTokens=$cached"
          )
        case Left(error) =>
          println(s"${item.customId}: ERROR ${error.message}")
      }
    }
  }
}
