package io.cequence.openaiscala.vertexai

import io.cequence.openaiscala.vertexai.JsonFormats._
import io.cequence.openaiscala.vertexai.domain.{
  BatchJobInput,
  BatchJobOutput,
  BatchPredictionJob,
  JobState,
  ListBatchPredictionJobsResponse
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class BatchPredictionJsonFormatsSpec extends AnyWordSpecLike with Matchers {

  "Batch prediction JSON formats" should {

    "deserialize a batch prediction job with GCS input/output" in {
      val job = Json
        .parse(
          """{
            |  "name": "projects/123/locations/us-central1/batchPredictionJobs/456",
            |  "displayName": "my batch job",
            |  "model": "publishers/google/models/gemini-2.5-flash-lite",
            |  "state": "JOB_STATE_SUCCEEDED",
            |  "inputConfig": {
            |    "instancesFormat": "jsonl",
            |    "gcsSource": {"uris": ["gs://bucket/input.jsonl"]}
            |  },
            |  "outputConfig": {
            |    "predictionsFormat": "jsonl",
            |    "gcsDestination": {"outputUriPrefix": "gs://bucket/output"}
            |  },
            |  "outputInfo": {"gcsOutputDirectory": "gs://bucket/output/prediction-123"},
            |  "completionStats": {"successfulCount": "2", "failedCount": "0"},
            |  "createTime": "2026-07-02T17:12:35.000Z",
            |  "endTime": "2026-07-02T17:30:00.000Z",
            |  "labels": {"team": "ml"}
            |}""".stripMargin
        )
        .as[BatchPredictionJob]

      job.name shouldBe "projects/123/locations/us-central1/batchPredictionJobs/456"
      job.state shouldBe Some(JobState.JOB_STATE_SUCCEEDED)
      job.isTerminated shouldBe true
      job.input shouldBe Some(BatchJobInput.Gcs(Seq("gs://bucket/input.jsonl")))
      job.output shouldBe Some(BatchJobOutput.Gcs("gs://bucket/output"))
      job.outputInfo.flatMap(_.gcsOutputDirectory) shouldBe
        Some("gs://bucket/output/prediction-123")
      job.completionStats.flatMap(_.successfulCount) shouldBe Some(2L)
      job.completionStats.flatMap(_.failedCount) shouldBe Some(0L)
      job.labels shouldBe Map("team" -> "ml")
    }

    "deserialize a running job with BigQuery input/output" in {
      val job = Json
        .parse(
          """{
            |  "name": "projects/123/locations/us-central1/batchPredictionJobs/789",
            |  "state": "JOB_STATE_RUNNING",
            |  "inputConfig": {
            |    "instancesFormat": "bigquery",
            |    "bigquerySource": {"inputUri": "bq://project.dataset.input"}
            |  },
            |  "outputConfig": {
            |    "predictionsFormat": "bigquery",
            |    "bigqueryDestination": {"outputUri": "bq://project.dataset.output"}
            |  }
            |}""".stripMargin
        )
        .as[BatchPredictionJob]

      job.isTerminated shouldBe false
      job.input shouldBe Some(BatchJobInput.BigQuery("bq://project.dataset.input"))
      job.output shouldBe Some(BatchJobOutput.BigQuery("bq://project.dataset.output"))
    }

    "deserialize a list response" in {
      val response = Json
        .parse(
          """{
            |  "batchPredictionJobs": [
            |    {"name": "projects/123/locations/us-central1/batchPredictionJobs/1"},
            |    {"name": "projects/123/locations/us-central1/batchPredictionJobs/2"}
            |  ],
            |  "nextPageToken": "token123"
            |}""".stripMargin
        )
        .as[ListBatchPredictionJobsResponse]

      response.batchPredictionJobs should have size 2
      response.nextPageToken shouldBe Some("token123")

      Json.parse("{}").as[ListBatchPredictionJobsResponse].batchPredictionJobs shouldBe empty
    }
  }
}
