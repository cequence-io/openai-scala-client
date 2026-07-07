package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.{
  ListMessageBatchesResponse,
  MessageBatch,
  MessageBatchDeleteResponse,
  MessageBatchIndividualResponse,
  MessageBatchProcessingStatus,
  MessageBatchResult
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class MessageBatchJsonFormatsSpec extends AnyWordSpecLike with Matchers with JsonFormats {

  // shape from https://platform.claude.com/docs/en/api/creating-message-batches
  private val batchJson =
    """{
      |  "id": "msgbatch_013Zva2CMHLNnXjNJJKqJ2EF",
      |  "type": "message_batch",
      |  "processing_status": "in_progress",
      |  "request_counts": {
      |    "processing": 100,
      |    "succeeded": 50,
      |    "errored": 30,
      |    "canceled": 10,
      |    "expired": 10
      |  },
      |  "ended_at": null,
      |  "created_at": "2024-09-24T18:37:24.100435Z",
      |  "expires_at": "2024-09-25T18:37:24.100435Z",
      |  "cancel_initiated_at": null,
      |  "results_url": null
      |}""".stripMargin

  "Message Batch JSON formats" should {

    "deserialize a message batch" in {
      val batch = Json.parse(batchJson).as[MessageBatch]

      batch.id shouldBe "msgbatch_013Zva2CMHLNnXjNJJKqJ2EF"
      batch.processingStatus shouldBe MessageBatchProcessingStatus.in_progress
      batch.isEnded shouldBe false
      batch.requestCounts.processing shouldBe 100
      batch.requestCounts.succeeded shouldBe 50
      batch.requestCounts.errored shouldBe 30
      batch.requestCounts.canceled shouldBe 10
      batch.requestCounts.expired shouldBe 10
      batch.createdAt shouldBe "2024-09-24T18:37:24.100435Z"
      batch.expiresAt shouldBe "2024-09-25T18:37:24.100435Z"
      batch.endedAt shouldBe None
      batch.cancelInitiatedAt shouldBe None
      batch.resultsUrl shouldBe None
    }

    "deserialize an ended message batch with a results url" in {
      val batch = Json
        .parse(
          batchJson
            .replace(
              """"processing_status": "in_progress"""",
              """"processing_status": "ended""""
            )
            .replace(
              """"results_url": null""",
              """"results_url": "https://api.anthropic.com/v1/messages/batches/msgbatch_013Zva2CMHLNnXjNJJKqJ2EF/results""""
            )
        )
        .as[MessageBatch]

      batch.isEnded shouldBe true
      batch.resultsUrl shouldBe Some(
        "https://api.anthropic.com/v1/messages/batches/msgbatch_013Zva2CMHLNnXjNJJKqJ2EF/results"
      )
    }

    "deserialize a list response with pagination fields" in {
      val response = Json
        .parse(
          s"""{"data": [$batchJson], "first_id": "first", "has_more": true, "last_id": "last"}"""
        )
        .as[ListMessageBatchesResponse]

      response.data should have size 1
      response.hasMore shouldBe true
      response.firstId shouldBe Some("first")
      response.lastId shouldBe Some("last")
    }

    "deserialize a succeeded result line" in {
      val line =
        """{
          |  "custom_id": "my-request",
          |  "result": {
          |    "type": "succeeded",
          |    "message": {
          |      "id": "msg_014VwiXbi91y3JMjcpyGBHX5",
          |      "type": "message",
          |      "role": "assistant",
          |      "model": "claude-haiku-4-5",
          |      "content": [{"type": "text", "text": "Oslo"}],
          |      "stop_reason": "end_turn",
          |      "stop_sequence": null,
          |      "usage": {"input_tokens": 19, "output_tokens": 4}
          |    }
          |  }
          |}""".stripMargin

      val response = Json.parse(line).as[MessageBatchIndividualResponse]

      response.customId shouldBe "my-request"
      response.result match {
        case MessageBatchResult.Succeeded(message) =>
          message.text shouldBe "Oslo"
          message.stop_reason shouldBe Some("end_turn")
        case other => fail(s"Expected a succeeded result, got: $other")
      }
    }

    "deserialize errored, canceled, and expired result lines" in {
      val errored = Json
        .parse(
          """{
            |  "custom_id": "bad-request",
            |  "result": {
            |    "type": "errored",
            |    "error": {
            |      "type": "error",
            |      "error": {"type": "invalid_request_error", "message": "max_tokens: required"},
            |      "request_id": "req_123"
            |    }
            |  }
            |}""".stripMargin
        )
        .as[MessageBatchIndividualResponse]

      errored.result match {
        case MessageBatchResult.Errored(error, requestId) =>
          error.`type` shouldBe "invalid_request_error"
          error.message shouldBe "max_tokens: required"
          requestId shouldBe Some("req_123")
        case other => fail(s"Expected an errored result, got: $other")
      }

      Json
        .parse("""{"custom_id": "c", "result": {"type": "canceled"}}""")
        .as[MessageBatchIndividualResponse]
        .result shouldBe MessageBatchResult.Canceled

      Json
        .parse("""{"custom_id": "e", "result": {"type": "expired"}}""")
        .as[MessageBatchIndividualResponse]
        .result shouldBe MessageBatchResult.Expired
    }

    "deserialize a delete response" in {
      val response = Json
        .parse(
          """{"id": "msgbatch_013Zva2CMHLNnXjNJJKqJ2EF", "type": "message_batch_deleted"}"""
        )
        .as[MessageBatchDeleteResponse]

      response.id shouldBe "msgbatch_013Zva2CMHLNnXjNJJKqJ2EF"
    }
  }
}
