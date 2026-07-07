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
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}

import scala.concurrent.Future

/**
 * The recommended pattern for large production batches (thousands of requests) on the
 * Anthropic Message Batches API (50% of standard cost), through the OpenAI adapter: the
 * submission, the status check, and the results retrieval run as separate invocations -
 * typically separate processes - connected only by the batch id (`msgbatch_...`), which the
 * API resolves statelessly. Results stay available for 29 days.
 *
 * Usage (each step can run hours apart, from different machines) - `status`/`results`/`delete`
 * need the model the batch was submitted with too, since a batch id alone isn't a routing key
 * (see [[io.cequence.openaiscala.service.OpenAIChatCompletionBatchService]]):
 * {{{
 * runMain ...AnthropicCreateChatCompletionBatchSplitFlowWithOpenAIAdapter submit                    // prints the batch id
 * runMain ...AnthropicCreateChatCompletionBatchSplitFlowWithOpenAIAdapter status <batchId> <model>
 * runMain ...AnthropicCreateChatCompletionBatchSplitFlowWithOpenAIAdapter results <batchId> <model>
 * runMain ...AnthropicCreateChatCompletionBatchSplitFlowWithOpenAIAdapter delete <batchId> <model>
 * }}}
 *
 * Requires `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateChatCompletionBatchSplitFlowWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService with OpenAIChatCompletionBatchService] {

  override val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    AnthropicServiceFactory.asOpenAI()

  private var mainArgs: Array[String] = Array.empty

  override def main(args: Array[String]): Unit = {
    mainArgs = args
    super.main(args)
  }

  override protected def run: Future[_] =
    mainArgs.headOption match {
      case Some("submit") =>
        service
          .createChatCompletionBatch(
            Seq(
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
            ),
            settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_4_5)
          )
          .map(info =>
            // persist this id - it is all that is needed to pick the batch up later
            println(s"submitted: ${info.id} (${info.providerStatus})")
          )

      case Some("status") =>
        service
          .getChatCompletionBatch(mainArgs(1), mainArgs(2))
          .map(info =>
            println(
              s"status: ${info.status} (provider: ${info.providerStatus}, done: ${info.isDone})"
            )
          )

      case Some("results") =>
        service
          .retrieveChatCompletionBatchResults(mainArgs(1), mainArgs(2))
          .map(_.foreach { item =>
            item.result match {
              case Right(response) => println(s"${item.customId}: ${response.contentHead}")
              case Left(error)     => println(s"${item.customId}: ERROR ${error.message}")
            }
          })

      case Some("delete") =>
        service
          .deleteChatCompletionBatch(mainArgs(1), mainArgs(2))
          .map(_ => println("deleted"))

      case _ =>
        Future.successful(
          println(
            "usage: submit | status <batchId> <model> | results <batchId> <model> | delete <batchId> <model>"
          )
        )
    }
}
