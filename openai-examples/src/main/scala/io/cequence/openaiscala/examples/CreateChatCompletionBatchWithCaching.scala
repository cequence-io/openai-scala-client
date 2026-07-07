package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Batch processing and prompt caching side by side on the OpenAI Batch API. Unlike Anthropic
 * (explicit `cache_control` blocks) and Gemini (explicit `CachedContent` resources), OpenAI's
 * prompt caching is '''purely implicit''': it activates automatically for prompts with a
 * shared prefix of 1024+ tokens - there is nothing to configure, no cache object, and no cache
 * name to pass around.
 *
 * The example first demonstrates the implicit cache on the synchronous API (call 1 writes the
 * cache, call 2 - sharing the same large system-message prefix - reports `cached_tokens` > 0),
 * then runs two batches with the very same prefix. Observed live (July 2026): the batches
 * report `cached_tokens` = 0 on every request - '''implicit caching does not apply to the
 * Batch API''' - so the batch's 50% discount stands alone and there is no point engineering
 * batch prompts for cache hits on OpenAI. (Compare with the Anthropic and Gemini counterparts
 * of this example, where the discounts do stack.)
 *
 * Cache hits are reported via `usage.prompt_tokens_details.cached_tokens` on both paths.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object CreateChatCompletionBatchWithCaching extends ExampleBase[OpenAIService] {

  override val service: OpenAIService = OpenAIServiceFactory()

  private val model = ModelId.gpt_4o_mini

  private val settings = CreateChatCompletionSettings(model)

  // ~5k tokens of deterministic shared context - well above OpenAI's 1024-token minimum
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

  private val systemMessage = SystemMessage(
    "You are a concise assistant. The following background context may or may not be " +
      s"relevant - answer from general knowledge when it is not.\n\n$sharedContext"
  )

  private def requests(batchTag: String) = Seq("Norway", "Sweden", "Denmark").map { country =>
    ChatCompletionBatchRequest(
      customId = s"${country.toLowerCase}-$batchTag",
      messages = Seq(
        systemMessage,
        UserMessage(s"What is the capital of $country? Reply in one word.")
      )
    )
  }

  override protected def run: Future[_] =
    for {
      // Call 1 (synchronous): writes the implicit cache for the shared prefix - nothing to
      // enable, caching just happens for 1024+ token prefixes.
      response1 <- service.createChatCompletion(
        messages =
          Seq(systemMessage, UserMessage("What is the capital of Finland? One word.")),
        settings = settings
      )
      _ = reportSync("call 1 (sync, cache cold)", response1)

      // Call 2 (synchronous): same prefix - the implicit cache reports hits.
      response2 <- service.createChatCompletion(
        messages =
          Seq(systemMessage, UserMessage("What is the capital of Iceland? One word.")),
        settings = settings
      )
      _ = reportSync("call 2 (sync, cache warm)", response2)

      // The batches share the very same prefix - yet implicit caching does not kick in on
      // the Batch API (cachedTokens stays 0); only the 50% batch discount applies.
      resultsA <- service.createChatCompletionBatchAndWaitForResults(
        requests("batch-a"),
        settings,
        pollingInterval = 30.seconds
      )
      _ = report("batch A", resultsA)

      resultsB <- service.createChatCompletionBatchAndWaitForResults(
        requests("batch-b"),
        settings,
        pollingInterval = 30.seconds
      )
      _ = report("batch B", resultsB)
    } yield ()

  private def reportSync(
    label: String,
    response: ChatCompletionResponse
  ): Unit =
    println(
      s"$label: '${response.contentHead}' promptTokens=${response.usage.map(_.prompt_tokens).getOrElse(0)} " +
        s"cachedTokens=${cachedTokens(response)}"
    )

  private def cachedTokens(response: ChatCompletionResponse): Int =
    response.usage.flatMap(_.prompt_tokens_details).map(_.cached_tokens).getOrElse(0)

  private def report(
    label: String,
    results: Seq[ChatCompletionBatchResultItem]
  ): Unit = {
    println(s"--- $label ---")
    results.foreach { item =>
      item.result match {
        case Right(response) =>
          println(
            s"${item.customId}: '${response.contentHead}' promptTokens=${response.usage
                .map(_.prompt_tokens)
                .getOrElse(0)} cachedTokens=${cachedTokens(response)}"
          )
        case Left(error) =>
          println(s"${item.customId}: ERROR ${error.message}")
      }
    }
  }
}
