package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Batch.{BatchEndpoint, CompletionWindow}
import io.cequence.openaiscala.domain.{FunctionSpec, ModelId}

import scala.concurrent.Future

object CreateBatch extends Example {
  override protected def run: Future[_] =
    for {
      assistant <- service.createBatch(
        inputFileId = "file-123",
        endpoint = BatchEndpoint.`/v1/chat/completions`,
        completionWindow = CompletionWindow.`24h`,
        metadata = Map(
          "customer_id" -> "user_123456789",
          "batch_description" -> "Nightly eval job"
        )
      )
    } yield println(assistant)
}
