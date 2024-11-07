package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Batch.{BatchEndpoint, CompletionWindow}

import scala.concurrent.Future

object CreateBatch extends Example {
  override protected def run: Future[_] =
    for {
      assistant <- service.createBatch(
        inputFileId = "file-xyz",
        endpoint = BatchEndpoint.`/v1/chat/completions`,
        completionWindow = CompletionWindow.`24h`,
        metadata = Map(
          "customer_id" -> "user_abc",
          "batch_description" -> "Nightly eval job"
        )
      )
    } yield println(assistant)
}
