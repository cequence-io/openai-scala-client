package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.Future

/**
 * The recommended pattern for large production batches (thousands of requests): the
 * submission, the status check, and the results retrieval run as separate invocations -
 * typically separate processes - connected only by the batch id, which every provider resolves
 * statelessly.
 *
 * Usage (each step can run hours apart, from different machines) - `status`/`results` need the
 * model the batch was submitted with too, since a batch id alone isn't a routing key (see
 * [[io.cequence.openaiscala.service.OpenAIChatCompletionBatchService]]):
 * {{{
 * runMain ...CreateChatCompletionBatchSplitFlow submit                    // prints the batch id
 * runMain ...CreateChatCompletionBatchSplitFlow status <batchId> <model>
 * runMain ...CreateChatCompletionBatchSplitFlow results <batchId> <model>
 * }}}
 *
 * The same code works with the Anthropic, Gemini, and Vertex AI adapters - swap the service
 * for `AnthropicServiceFactory.asOpenAI()`, `GeminiServiceFactory.asOpenAI()`, or
 * `VertexAIServiceFactory.asOpenAIWithBatchSupport(gcsBucket)` and adjust the model.
 */
object CreateChatCompletionBatchSplitFlow extends ExampleBase[OpenAIService] {

  override val service: OpenAIService = OpenAIServiceFactory()

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
            settings = CreateChatCompletionSettings(ModelId.gpt_4o_mini)
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

      case _ =>
        Future.successful(
          println("usage: submit | status <batchId> <model> | results <batchId> <model>")
        )
    }
}
