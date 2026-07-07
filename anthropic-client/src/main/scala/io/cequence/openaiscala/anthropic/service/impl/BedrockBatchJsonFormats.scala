package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.anthropic.domain.{
  BatchInferenceJob,
  BatchInferenceJobStatus,
  ListBatchInferenceJobsResponse
}
import io.cequence.wsclient.JsonUtil
import play.api.libs.json.{JsSuccess, Reads}

/**
 * JSON formats for the Bedrock batch-inference control-plane responses
 * (`GetModelInvocationJob` / `ListModelInvocationJobs`), which are camelCase and nest the S3
 * input/output location under a union type (`inputDataConfig.s3InputDataConfig.s3Uri` etc.).
 */
trait BedrockBatchJsonFormats {

  implicit lazy val batchInferenceJobStatusReads: Reads[BatchInferenceJobStatus] =
    JsonUtil.enumFormat[BatchInferenceJobStatus](BatchInferenceJobStatus.values: _*)

  implicit lazy val batchInferenceJobReads: Reads[BatchInferenceJob] = Reads { json =>
    for {
      jobArn <- (json \ "jobArn").validate[String]
    } yield BatchInferenceJob(
      jobArn = jobArn,
      jobName = (json \ "jobName").asOpt[String],
      modelId = (json \ "modelId").asOpt[String],
      status = (json \ "status").asOpt[BatchInferenceJobStatus],
      message = (json \ "message").asOpt[String],
      submitTime = (json \ "submitTime").asOpt[String],
      lastModifiedTime = (json \ "lastModifiedTime").asOpt[String],
      endTime = (json \ "endTime").asOpt[String],
      inputS3Uri = (json \ "inputDataConfig" \ "s3InputDataConfig" \ "s3Uri").asOpt[String],
      outputS3Uri = (json \ "outputDataConfig" \ "s3OutputDataConfig" \ "s3Uri").asOpt[String],
      roleArn = (json \ "roleArn").asOpt[String]
    )
  }

  implicit lazy val listBatchInferenceJobsResponseReads
    : Reads[ListBatchInferenceJobsResponse] =
    Reads { json =>
      JsSuccess(
        ListBatchInferenceJobsResponse(
          invocationJobSummaries =
            (json \ "invocationJobSummaries").asOpt[Seq[BatchInferenceJob]].getOrElse(Nil),
          nextToken = (json \ "nextToken").asOpt[String]
        )
      )
    }
}
