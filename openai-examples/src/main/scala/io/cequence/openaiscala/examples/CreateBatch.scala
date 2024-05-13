package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.Batch.{BatchEndpoint, CompletionWindow}

import scala.concurrent.Future

object CreateBatch extends Example {
  override protected def run: Future[_] =
    for {
      assistant <- service.createBatch(
//        inputFileId = "file-mjdvW9DTeWDXO2g6sks1kvuQ",
//        inputFileId = "file-bRFkk72miUWa48tDrE9b2lnL",
        inputFileId = "file-8v4jKZa0cviulgJLnEofCW1N",
        endpoint = BatchEndpoint.`/v1/chat/completions`,
        completionWindow = CompletionWindow.`24h`,
        metadata = Map(
          "customer_id" -> "user_123456789",
          "batch_description" -> "Nightly eval job"
        )
      )
    } yield println(assistant)
}
