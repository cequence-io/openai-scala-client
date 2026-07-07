package io.cequence.openaiscala.examples.anthropic

import akka.pattern.after
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{
  MessageBatch,
  MessageBatchRequest,
  MessageBatchResult
}
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Live end-to-end check of the Message Batches API: create a small batch, list, poll until
 * processing ends (batches run at 50% cost; most finish well within an hour, though up to 24h
 * is allowed), print the results, and delete the batch.
 *
 * Requires `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateMessageBatchLive extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val settings = AnthropicCreateMessageSettings(
    model = NonOpenAIModelId.claude_haiku_4_5,
    max_tokens = 256
  )

  override protected def run: Future[_] =
    for {
      batch <- service.createMessageBatch(
        Seq(
          MessageBatchRequest(
            customId = "capital-norway",
            messages = Seq(UserMessage("What is the capital of Norway? Reply in one word.")),
            settings = settings
          ),
          MessageBatchRequest(
            customId = "capital-sweden",
            messages = Seq(UserMessage("What is the capital of Sweden? Reply in one word.")),
            settings = settings
          )
        )
      )
      _ = println(s"created: id=${batch.id} status=${batch.processingStatus}")

      batches <- service.listMessageBatches(limit = Some(5))
      _ = println(s"list: count=${batches.data.size} hasMore=${batches.hasMore}")

      ended <- pollUntilEnded(batch.id)
      _ = println(s"ended: counts=${ended.requestCounts}")

      results <- service.retrieveMessageBatchResults(batch.id)
      _ = results.foreach { result =>
        result.result match {
          case MessageBatchResult.Succeeded(message) =>
            println(s"${result.customId}: ${message.text}")
          case MessageBatchResult.Errored(error, _) =>
            println(s"${result.customId}: ERROR ${error.`type`} - ${error.message}")
          case other =>
            println(s"${result.customId}: ${other.`type`}")
        }
      }

      deleted <- service.deleteMessageBatch(batch.id)
      _ = println(s"deleted: ${deleted.id}")
    } yield ()

  private def pollUntilEnded(batchId: String): Future[MessageBatch] =
    service.getMessageBatch(batchId).flatMap { batch =>
      if (batch.isEnded)
        Future.successful(batch)
      else {
        println(s"polling: status=${batch.processingStatus}...")
        after(10.seconds, scheduler)(pollUntilEnded(batchId))
      }
    }
}
