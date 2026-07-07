package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.{
  BatchOutput,
  BatchRequestItem,
  BatchState,
  ChatRole,
  Content,
  GenerateContentBatch
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class BatchJsonFormatsSpec extends AnyWordSpecLike with Matchers {

  "Batch JSON formats" should {

    "deserialize a pending batch with int64-as-string stats" in {
      val batch = Json
        .parse(
          """{
            |  "name": "batches/abc123",
            |  "displayName": "my batch",
            |  "model": "models/gemini-2.5-flash-lite",
            |  "state": "BATCH_STATE_PENDING",
            |  "createTime": "2026-07-02T17:12:35.000Z",
            |  "updateTime": "2026-07-02T17:12:35.000Z",
            |  "batchStats": {"requestCount": "2", "pendingRequestCount": "2"},
            |  "priority": "10"
            |}""".stripMargin
        )
        .as[GenerateContentBatch]

      batch.name shouldBe "batches/abc123"
      batch.displayName shouldBe Some("my batch")
      batch.model shouldBe Some("models/gemini-2.5-flash-lite")
      batch.state shouldBe Some(BatchState.BATCH_STATE_PENDING)
      batch.isTerminated shouldBe false
      batch.priority shouldBe Some(10L)
      batch.batchStats.flatMap(_.requestCount) shouldBe Some(2L)
      batch.batchStats.flatMap(_.pendingRequestCount) shouldBe Some(2L)
    }

    "deserialize a succeeded batch output with wrapped inlined responses" in {
      val output = Json
        .parse(
          """{
            |  "inlinedResponses": {
            |    "inlinedResponses": [
            |      {
            |        "metadata": {"key": "request-1"},
            |        "response": {
            |          "candidates": [
            |            {"content": {"parts": [{"text": "Oslo"}], "role": "model"}}
            |          ],
            |          "usageMetadata": {"promptTokenCount": 9, "totalTokenCount": 10},
            |          "modelVersion": "gemini-2.5-flash-lite"
            |        }
            |      },
            |      {
            |        "metadata": {"key": "request-2"},
            |        "error": {"code": 3, "message": "invalid request"}
            |      }
            |    ]
            |  }
            |}""".stripMargin
        )
        .as[BatchOutput]

      output.responsesFile shouldBe None
      output.inlinedResponses should have size 2

      val succeeded = output.inlinedResponses.head
      succeeded.key shouldBe Some("request-1")
      succeeded.response.map(_.contentHeadText) shouldBe Some("Oslo")
      succeeded.error shouldBe None

      val errored = output.inlinedResponses(1)
      errored.key shouldBe Some("request-2")
      errored.response shouldBe None
      errored.error.flatMap(_.code) shouldBe Some(3)
      errored.error.flatMap(_.message) shouldBe Some("invalid request")
    }

    "deserialize a file-based batch output" in {
      val output =
        Json.parse("""{"responsesFile": "files/batch-results-123"}""").as[BatchOutput]

      output.responsesFile shouldBe Some("files/batch-results-123")
      output.inlinedResponses shouldBe empty
    }

    "recognize terminal states" in {
      Seq(
        BatchState.BATCH_STATE_SUCCEEDED,
        BatchState.BATCH_STATE_FAILED,
        BatchState.BATCH_STATE_CANCELLED,
        BatchState.BATCH_STATE_EXPIRED
      ).foreach { state =>
        GenerateContentBatch(
          name = "batches/x",
          state = Some(state)
        ).isTerminated shouldBe true
      }

      Seq(BatchState.BATCH_STATE_PENDING, BatchState.BATCH_STATE_RUNNING).foreach { state =>
        GenerateContentBatch(
          name = "batches/x",
          state = Some(state)
        ).isTerminated shouldBe false
      }
    }

    "default a batch request item to no per-item systemInstruction when omitted" in {
      val item = BatchRequestItem(
        key = "request-1",
        contents = Seq(Content.textPart("Where is Oslo?", ChatRole.User))
      )

      item.systemInstruction shouldBe None
    }

    "carry an explicit per-item systemInstruction when provided" in {
      val systemInstruction = Content.textPart("You are a geography expert.", ChatRole.User)

      val item = BatchRequestItem(
        key = "request-1",
        contents = Seq(Content.textPart("Where is Oslo?", ChatRole.User)),
        systemInstruction = Some(systemInstruction)
      )

      item.systemInstruction shouldBe Some(systemInstruction)
    }

    "override the batch-wide system_instruction of a serialized inlined request with the " +
      "per-item one, when set" in {
        val batchWideRequestBody = Json.obj(
          "contents" -> Json.arr(Json.obj("parts" -> Json.arr(Json.obj("text" -> "hi")))),
          "system_instruction" -> Json.obj(
            "parts" -> Json.arr(Json.obj("text" -> "batch-wide system message"))
          ),
          "generation_config" -> Json.obj("temperature" -> 0.2)
        )
        val perItemSystemInstruction =
          Content.textPart("request-1's own system message", ChatRole.User)

        val requestBody = withOverriddenContentField(
          batchWideRequestBody,
          "system_instruction",
          Some(perItemSystemInstruction)
        )

        (requestBody \ "system_instruction").as[Content] shouldBe perItemSystemInstruction
        // unrelated fields are left untouched
        requestBody \ "contents" shouldBe batchWideRequestBody \ "contents"
        requestBody \ "generation_config" shouldBe batchWideRequestBody \ "generation_config"
      }

    "leave a serialized inlined request unchanged when no per-item systemInstruction is " +
      "set (e.g. explicit caching is in use)" in {
        val batchWideRequestBody = Json.obj(
          "contents" -> Json.arr(Json.obj("parts" -> Json.arr(Json.obj("text" -> "hi")))),
          "cached_content" -> "cachedContents/abc123"
        )

        val requestBody =
          withOverriddenContentField(batchWideRequestBody, "system_instruction", None)

        requestBody shouldBe batchWideRequestBody
      }
  }
}
