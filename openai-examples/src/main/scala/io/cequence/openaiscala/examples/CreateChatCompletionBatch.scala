package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Runs a provider-agnostic chat-completion batch (~50% of standard cost, async processing)
 * against the OpenAI Batch API: submits the requests, polls until processing ends, and prints
 * the results.
 *
 * The very same code works with the Anthropic, Gemini, and Vertex AI adapters - swap the
 * service for `AnthropicServiceFactory.asOpenAI()`, `GeminiServiceFactory.asOpenAI()`, or
 * `VertexAIServiceFactory.asOpenAIWithBatchSupport(gcsBucket)` and adjust the model.
 */
object CreateChatCompletionBatch extends ExampleBase[OpenAIService] {

  override val service: OpenAIService = OpenAIServiceFactory()

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
        settings = CreateChatCompletionSettings(ModelId.gpt_4o_mini),
        pollingInterval = 30.seconds
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
