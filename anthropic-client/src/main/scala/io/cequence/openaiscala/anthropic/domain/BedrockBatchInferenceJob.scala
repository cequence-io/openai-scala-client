package io.cequence.openaiscala.anthropic.domain

import io.cequence.wsclient.domain.EnumValue

/**
 * Status of a Bedrock batch inference job (`model-invocation-job`).
 *
 * @see
 *   <a
 *   href="https://docs.aws.amazon.com/bedrock/latest/APIReference/API_GetModelInvocationJob.html">GetModelInvocationJob
 *   Doc</a>
 */
sealed trait BatchInferenceJobStatus extends EnumValue

object BatchInferenceJobStatus {
  case object Submitted extends BatchInferenceJobStatus
  case object Validating extends BatchInferenceJobStatus
  case object Scheduled extends BatchInferenceJobStatus
  case object InProgress extends BatchInferenceJobStatus
  case object Completed extends BatchInferenceJobStatus
  case object PartiallyCompleted extends BatchInferenceJobStatus
  case object Failed extends BatchInferenceJobStatus
  case object Stopping extends BatchInferenceJobStatus
  case object Stopped extends BatchInferenceJobStatus
  case object Expired extends BatchInferenceJobStatus

  def values: Seq[BatchInferenceJobStatus] = Seq(
    Submitted,
    Validating,
    Scheduled,
    InProgress,
    Completed,
    PartiallyCompleted,
    Failed,
    Stopping,
    Stopped,
    Expired
  )

  /** Terminal states - the job will not change state anymore. */
  def terminalValues: Seq[BatchInferenceJobStatus] =
    Seq(Completed, PartiallyCompleted, Failed, Stopped, Expired)
}

/**
 * Parameters for creating a batch inference job (`POST /model-invocation-job`).
 *
 * @param jobName
 *   1-63 chars matching `[a-zA-Z0-9]{1,63}(-*[a-zA-Z0-9+\-.]){0,63}`.
 * @param modelId
 *   Model id or ARN, e.g. `anthropic.claude-haiku-4-5-20251001-v1:0` (a cross-region inference
 *   profile prefix such as `us.` may be required, as with on-demand Bedrock invoke).
 * @param roleArn
 *   ARN of an IAM service role trusted by `bedrock.amazonaws.com`, with permissions to read
 *   `inputS3Uri` and write `outputS3Uri`. See <a
 *   href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-iam-sr.html">Create a
 *   service role for batch inference</a>.
 * @param inputS3Uri
 *   `s3://` URI of a single `.jsonl` input file, or a folder containing one or more.
 * @param outputS3Uri
 *   `s3://` prefix under which the output `.jsonl` file(s) are written.
 */
final case class CreateBatchInferenceJobSettings(
  jobName: String,
  modelId: String,
  roleArn: String,
  inputS3Uri: String,
  outputS3Uri: String,
  timeoutDurationInHours: Option[Int] = None
)

/**
 * A Bedrock batch inference job.
 *
 * @see
 *   <a
 *   href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-inference.html">Bedrock
 *   Batch Inference Doc</a>
 */
final case class BatchInferenceJob(
  jobArn: String,
  jobName: Option[String] = None,
  modelId: Option[String] = None,
  status: Option[BatchInferenceJobStatus] = None,
  message: Option[String] = None,
  submitTime: Option[String] = None,
  lastModifiedTime: Option[String] = None,
  endTime: Option[String] = None,
  inputS3Uri: Option[String] = None,
  outputS3Uri: Option[String] = None,
  roleArn: Option[String] = None
) {
  def isTerminated: Boolean = status.exists(BatchInferenceJobStatus.terminalValues.contains)

  // the trailing short id - for display/logging only; Get/Stop calls require the full ARN
  // (jobArn), a bare id is rejected by the live API despite AWS docs claiming otherwise.
  def jobId: String = jobArn.split("/").last
}

/** Response envelope of the list-jobs endpoint (`GET /model-invocation-jobs`). */
final case class ListBatchInferenceJobsResponse(
  invocationJobSummaries: Seq[BatchInferenceJob],
  nextToken: Option[String] = None
)
