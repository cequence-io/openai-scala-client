package io.cequence.openaiscala.examples.groq

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Groq's Batch API (`/openai/v1/batches`) is close enough to a drop-in clone of OpenAI's own
 * that this codebase's '''unmodified''' unified batch implementation works against it directly
 *   - just point [[OpenAIServiceFactory.customInstance]] at Groq's base URL - no Groq-specific
 *     client module needed (unlike Anthropic/Gemini/Vertex AI).
 *
 * The one compatibility gap found: Groq's file objects (from the Files API, used to stage the
 * JSONL input) omit the `status` field that OpenAI's always include - `FileInfo.status` is
 * `Option[String]` for exactly this reason. Everything else - submit, poll, retrieve, delete -
 * matches OpenAI's shapes closely enough to work as-is (verified live: a 2-request batch went
 * `validating` -> `completed` in ~10 seconds, well inside Groq's 50%-discount, 24h completion
 * window).
 *
 * `OpenAIChatCompletionServiceFactory`/`ChatCompletionProvider.groq` (used by the other Groq
 * examples) expose only the minimal `OpenAIChatCompletionService` interface, which has no real
 * batch implementation behind it - batch support requires the full [[OpenAIService]], hence
 * `OpenAIServiceFactory.customInstance` here instead.
 *
 * Requires `GROQ_API_KEY`.
 */
object GroqCreateChatCompletionBatch extends ExampleBase[OpenAIService] {

  override val service: OpenAIService = OpenAIServiceFactory.customInstance(
    coreUrl = "https://api.groq.com/openai/v1/",
    requestContext = WsRequestContext(
      authHeaders = Seq(("Authorization", s"Bearer ${sys.env("GROQ_API_KEY")}"))
    )
  )

  private val model = NonOpenAIModelId.llama_3_3_70b_versatile

  private val requests = Seq(
    ChatCompletionBatchRequest(
      customId = "capital-norway",
      messages = Seq(
        SystemMessage("You are a concise assistant."),
        UserMessage("What is the capital of Norway? Reply in one word.")
      )
    ),
    ChatCompletionBatchRequest(
      customId = "capital-sweden",
      messages = Seq(
        SystemMessage("You are a concise assistant."),
        UserMessage("What is the capital of Sweden? Reply in one word.")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionBatchAndWaitForResults(
        requests,
        settings = CreateChatCompletionSettings(model),
        pollingInterval = 10.seconds,
        deleteBatchAfterUse = true
      )
      .map(_.foreach { item =>
        item.result match {
          case Right(response) =>
            println(s"${item.customId}: ${response.contentHead}")
          case Left(error) =>
            println(s"${item.customId}: ERROR ${error.message}")
        }
      })
}
