package io.cequence.openaiscala.vertexai.domain

import io.cequence.wsclient.domain.EnumValue

/**
 * State of a Vertex AI batch prediction job.
 *
 * @see
 *   <a
 *   href="https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/batch-prediction-api">Vertex
 *   AI Batch Prediction Docs</a>
 */
sealed trait JobState extends EnumValue

object JobState {
  case object JOB_STATE_UNSPECIFIED extends JobState
  case object JOB_STATE_QUEUED extends JobState
  case object JOB_STATE_PENDING extends JobState
  case object JOB_STATE_RUNNING extends JobState
  case object JOB_STATE_SUCCEEDED extends JobState
  case object JOB_STATE_FAILED extends JobState
  case object JOB_STATE_CANCELLING extends JobState
  case object JOB_STATE_CANCELLED extends JobState
  case object JOB_STATE_PAUSED extends JobState
  case object JOB_STATE_EXPIRED extends JobState
  case object JOB_STATE_UPDATING extends JobState
  case object JOB_STATE_PARTIALLY_SUCCEEDED extends JobState

  def values: Seq[JobState] = Seq(
    JOB_STATE_UNSPECIFIED,
    JOB_STATE_QUEUED,
    JOB_STATE_PENDING,
    JOB_STATE_RUNNING,
    JOB_STATE_SUCCEEDED,
    JOB_STATE_FAILED,
    JOB_STATE_CANCELLING,
    JOB_STATE_CANCELLED,
    JOB_STATE_PAUSED,
    JOB_STATE_EXPIRED,
    JOB_STATE_UPDATING,
    JOB_STATE_PARTIALLY_SUCCEEDED
  )

  /** Terminal states - the job will not change state anymore. */
  def terminalValues: Seq[JobState] = Seq(
    JOB_STATE_SUCCEEDED,
    JOB_STATE_FAILED,
    JOB_STATE_CANCELLED,
    JOB_STATE_EXPIRED,
    JOB_STATE_PARTIALLY_SUCCEEDED
  )
}

/** Input of a batch prediction job. */
sealed trait BatchJobInput

object BatchJobInput {

  /**
   * JSONL files in Cloud Storage; each line is `{"request": {"contents": [...], ...}}`.
   *
   * @param uris
   *   `gs://` URIs of the input files.
   */
  final case class Gcs(uris: Seq[String]) extends BatchJobInput {
    val instancesFormat: String = "jsonl"
  }

  object Gcs {
    def apply(uri: String): Gcs = Gcs(Seq(uri))
  }

  /**
   * A BigQuery table with a `request` column.
   *
   * @param inputUri
   *   `bq://project.dataset.table`.
   */
  final case class BigQuery(inputUri: String) extends BatchJobInput {
    val instancesFormat: String = "bigquery"
  }
}

/** Output destination of a batch prediction job. */
sealed trait BatchJobOutput

object BatchJobOutput {

  /**
   * @param outputUriPrefix
   *   `gs://` prefix under which the prediction JSONL files are written.
   */
  final case class Gcs(outputUriPrefix: String) extends BatchJobOutput {
    val predictionsFormat: String = "jsonl"
  }

  /**
   * @param outputUri
   *   `bq://project.dataset.table` (the table is created if absent).
   */
  final case class BigQuery(outputUri: String) extends BatchJobOutput {
    val predictionsFormat: String = "bigquery"
  }
}

/** A `google.rpc.Status` error attached to a failed job. */
final case class BatchJobError(
  code: Option[Int] = None,
  message: Option[String] = None
)

/** Locations of the produced predictions. */
final case class BatchJobOutputInfo(
  gcsOutputDirectory: Option[String] = None,
  bigqueryOutputDataset: Option[String] = None,
  bigqueryOutputTable: Option[String] = None
)

/** Request counts of a finished (or partially finished) job. */
final case class BatchJobCompletionStats(
  successfulCount: Option[Long] = None,
  failedCount: Option[Long] = None,
  incompleteCount: Option[Long] = None
)

/**
 * Parameters for creating a batch prediction job (`POST
 * /v1/projects/{project}/locations/{location}/batchPredictionJobs`).
 *
 * @param model
 *   Model to run, e.g. `gemini-2.5-flash-lite` (a bare id is expanded to
 *   `publishers/google/models/{id}`).
 */
final case class CreateBatchPredictionJobSettings(
  displayName: String,
  model: String,
  input: BatchJobInput,
  output: BatchJobOutput,
  labels: Map[String, String] = Map.empty
)

/**
 * A Vertex AI batch prediction job.
 *
 * @see
 *   <a
 *   href="https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/batch-prediction-api">Vertex
 *   AI Batch Prediction Docs</a>
 */
final case class BatchPredictionJob(
  name: String,
  displayName: Option[String] = None,
  model: Option[String] = None,
  modelVersionId: Option[String] = None,
  state: Option[JobState] = None,
  error: Option[BatchJobError] = None,
  input: Option[BatchJobInput] = None,
  output: Option[BatchJobOutput] = None,
  outputInfo: Option[BatchJobOutputInfo] = None,
  completionStats: Option[BatchJobCompletionStats] = None,
  createTime: Option[String] = None,
  startTime: Option[String] = None,
  endTime: Option[String] = None,
  updateTime: Option[String] = None,
  labels: Map[String, String] = Map.empty
) {
  def isTerminated: Boolean = state.exists(JobState.terminalValues.contains)
}

/** Response envelope of the list-jobs endpoint. */
final case class ListBatchPredictionJobsResponse(
  batchPredictionJobs: Seq[BatchPredictionJob],
  nextPageToken: Option[String] = None
)
