package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.{
  BatchInferenceJob,
  BatchInferenceJobStatus,
  CreateBatchInferenceJobSettings,
  ListBatchInferenceJobsResponse
}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * AWS Bedrock's own batch inference API (`model-invocation-job`), processed at 50% of standard
 * on-demand cost. This is distinct from Anthropic's direct Message Batches API, which Bedrock
 * does not expose. Input comes from an S3 JSONL file (one `{"recordId", "modelInput"}` object
 * per line, where `modelInput` is the same body used for a regular Bedrock Invoke call);
 * output is written back to S3 as JSONL.
 *
 * Authenticates with AWS SigV4 (access key + secret + region, optionally a session token) -
 * see [[io.cequence.openaiscala.anthropic.service.impl.AnthropicBedrockServiceImpl]] for the
 * same auth model used by on-demand Bedrock invoke.
 *
 * Note: unlike the direct Anthropic Message Batches API and Vertex AI's batch prediction jobs,
 * Bedrock batch inference jobs cannot be deleted - only stopped ([[stopBatchInferenceJob]]).
 * The job record itself is retained by AWS; only the S3 artifacts you staged can be cleaned
 * up.
 *
 * Note: prompt caching (`cache_control`) is not supported here - a `modelInput` that carries
 * one still passes job submission/validation, but every such record then fails individually
 * during actual invocation ("You invoked an unsupported model or your request did not allow
 * prompt caching"), even though the job itself still reaches a `Completed` status with
 * `successRecordCount=0`. Live-verified: the identical request succeeds when `cache_control`
 * is omitted.
 *
 * @see
 *   <a
 *   href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-inference.html">Bedrock
 *   Batch Inference Doc</a>
 */
trait AnthropicBedrockBatchInferenceService extends CloseableService {

  def createBatchInferenceJob(
    settings: CreateBatchInferenceJobSettings
  ): Future[BatchInferenceJob]

  def getBatchInferenceJob(jobIdentifier: String): Future[BatchInferenceJob]

  def listBatchInferenceJobs(
    maxResults: Option[Int] = None,
    nextToken: Option[String] = None,
    statusEquals: Option[BatchInferenceJobStatus] = None,
    nameContains: Option[String] = None
  ): Future[ListBatchInferenceJobsResponse]

  def stopBatchInferenceJob(jobIdentifier: String): Future[Unit]
}
