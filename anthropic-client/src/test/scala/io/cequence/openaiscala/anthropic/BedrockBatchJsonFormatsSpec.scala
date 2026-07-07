package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.{
  BatchInferenceJobStatus,
  ListBatchInferenceJobsResponse
}
import io.cequence.openaiscala.anthropic.service.impl.BedrockBatchJsonFormats
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class BedrockBatchJsonFormatsSpec
    extends AnyWordSpecLike
    with Matchers
    with BedrockBatchJsonFormats {

  // shape from https://docs.aws.amazon.com/bedrock/latest/APIReference/API_GetModelInvocationJob.html
  private val jobJson =
    """{
      |  "jobArn": "arn:aws:bedrock:us-east-1:123456789012:model-invocation-job/abcdefghijkl",
      |  "jobName": "my-batch-job",
      |  "modelId": "anthropic.claude-haiku-4-5-20251001-v1:0",
      |  "status": "InProgress",
      |  "submitTime": "2026-06-01T12:00:00Z",
      |  "lastModifiedTime": "2026-06-01T12:05:00Z",
      |  "roleArn": "arn:aws:iam::123456789012:role/MyBatchInferenceRole",
      |  "inputDataConfig": {
      |    "s3InputDataConfig": { "s3Uri": "s3://input-bucket/abc.jsonl" }
      |  },
      |  "outputDataConfig": {
      |    "s3OutputDataConfig": { "s3Uri": "s3://output-bucket/" }
      |  }
      |}""".stripMargin

  "Bedrock batch inference JSON formats" should {

    "deserialize a batch inference job" in {
      val job =
        Json.parse(jobJson).as[io.cequence.openaiscala.anthropic.domain.BatchInferenceJob]

      job.jobArn shouldBe "arn:aws:bedrock:us-east-1:123456789012:model-invocation-job/abcdefghijkl"
      job.jobId shouldBe "abcdefghijkl"
      job.jobName shouldBe Some("my-batch-job")
      job.modelId shouldBe Some("anthropic.claude-haiku-4-5-20251001-v1:0")
      job.status shouldBe Some(BatchInferenceJobStatus.InProgress)
      job.isTerminated shouldBe false
      job.inputS3Uri shouldBe Some("s3://input-bucket/abc.jsonl")
      job.outputS3Uri shouldBe Some("s3://output-bucket/")
      job.roleArn shouldBe Some("arn:aws:iam::123456789012:role/MyBatchInferenceRole")
    }

    "mark terminal statuses correctly" in {
      Seq("Completed", "PartiallyCompleted", "Failed", "Stopped", "Expired").foreach {
        status =>
          val job = Json
            .parse(jobJson.replace(""""status": "InProgress"""", s""""status": "$status""""))
            .as[io.cequence.openaiscala.anthropic.domain.BatchInferenceJob]

          job.isTerminated shouldBe true
      }

      Seq("Submitted", "Validating", "Scheduled", "InProgress", "Stopping").foreach { status =>
        val job = Json
          .parse(jobJson.replace(""""status": "InProgress"""", s""""status": "$status""""))
          .as[io.cequence.openaiscala.anthropic.domain.BatchInferenceJob]

        job.isTerminated shouldBe false
      }
    }

    "deserialize a list response with pagination" in {
      val response = Json
        .parse(s"""{"invocationJobSummaries": [$jobJson], "nextToken": "token123"}""")
        .as[ListBatchInferenceJobsResponse]

      response.invocationJobSummaries should have size 1
      response.nextToken shouldBe Some("token123")

      Json.parse("{}").as[ListBatchInferenceJobsResponse].invocationJobSummaries shouldBe empty
    }
  }
}
