package io.cequence.openaiscala.vertexai.service

import io.cequence.openaiscala.vertexai.domain.{
  BatchPredictionJob,
  CreateBatchPredictionJobSettings,
  ListBatchPredictionJobsResponse
}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * Vertex AI batch prediction API for Gemini models
 * (`projects/{project}/locations/{location}/batchPredictionJobs`), processed at 50% of
 * standard cost. Input comes from Cloud Storage (JSONL, one `{"request": {...}}` per line) or
 * BigQuery; output goes to Cloud Storage or BigQuery.
 *
 * Authenticates with Google Application Default Credentials (`gcloud auth application-default
 * login`, or a service account via `GOOGLE_APPLICATION_CREDENTIALS`).
 *
 * @see
 *   <a
 *   href="https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/batch-prediction-api">Vertex
 *   AI Batch Prediction Docs</a>
 */
trait VertexAIBatchPredictionService extends CloseableService {

  /**
   * Creates a batch prediction job (`POST /v1/.../batchPredictionJobs`).
   *
   * @return
   *   the created job (state `JOB_STATE_PENDING`); poll with [[getBatchPredictionJob]] until
   *   terminal.
   */
  def createBatchPredictionJob(
    settings: CreateBatchPredictionJobSettings
  ): Future[BatchPredictionJob]

  /**
   * Retrieves a batch prediction job (`GET /v1/.../batchPredictionJobs/{id}`). Poll until
   * [[BatchPredictionJob.state]] is terminal; on success the predictions are at
   * `outputInfo.gcsOutputDirectory` or `outputInfo.bigqueryOutputTable`.
   *
   * @param jobId
   *   Job id or full resource name.
   */
  def getBatchPredictionJob(jobId: String): Future[BatchPredictionJob]

  /**
   * Lists batch prediction jobs (`GET /v1/.../batchPredictionJobs`).
   *
   * @param filter
   *   Optional list filter, e.g. `state="JOB_STATE_SUCCEEDED"`.
   */
  def listBatchPredictionJobs(
    pageSize: Option[Int] = None,
    pageToken: Option[String] = None,
    filter: Option[String] = None
  ): Future[ListBatchPredictionJobsResponse]

  /**
   * Cancels a batch prediction job (`POST /v1/.../batchPredictionJobs/{id}:cancel`). The job
   * moves through `JOB_STATE_CANCELLING` to `JOB_STATE_CANCELLED`.
   *
   * @param jobId
   *   Job id or full resource name.
   */
  def cancelBatchPredictionJob(jobId: String): Future[Unit]

  /**
   * Deletes a batch prediction job (`DELETE /v1/.../batchPredictionJobs/{id}`). Only jobs in a
   * terminal state can be deleted - cancel a running job first.
   *
   * @param jobId
   *   Job id or full resource name.
   */
  def deleteBatchPredictionJob(jobId: String): Future[Unit]
}
