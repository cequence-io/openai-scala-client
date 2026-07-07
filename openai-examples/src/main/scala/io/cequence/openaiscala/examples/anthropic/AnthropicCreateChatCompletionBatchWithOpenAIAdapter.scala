package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
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
 * Runs a provider-agnostic chat-completion batch against the Anthropic Message Batches API
 * (50% of standard cost) through the OpenAI adapter: submits the requests, polls until
 * processing ends, prints the results, and deletes the batch.
 *
 * Requires `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateChatCompletionBatchWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService with OpenAIChatCompletionBatchService] {

  override val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    AnthropicServiceFactory.asOpenAI()

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
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_4_5),
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
