package io.cequence.openaiscala.vertexai.service

import com.google.cloud.vertexai.VertexAI
import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.service.OpenAIChatCompletionBatchService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.vertexai.service.impl.{
  OpenAIVertexAIChatCompletionService,
  VertexAIBatchPredictionServiceImpl
}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object VertexAIServiceFactory extends EnvHelper {

  private val projectIdKey = "VERTEXAI_PROJECT_ID"
  private val locationIdKey = "VERTEXAI_LOCATION"

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the AnthropicService
   *
   * @param projectId
   * @param location
   * @param ec
   * @return
   */
  def asOpenAI(
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey)
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService =
    new OpenAIVertexAIChatCompletionService(
      VertexAIServiceFactory(projectId, location)
    )

  private def apply(
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey)
  ): VertexAI =
    new VertexAI(projectId, location)

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping VertexAI with the
   * provider-agnostic chat-completion batch endpoints enabled (`createChatCompletionBatch`,
   * ...). Vertex AI batch prediction has no inline input, so batches are staged as JSONL files
   * in the given Cloud Storage bucket, which the Application Default Credentials must be
   * allowed to read/write.
   *
   * @param gcsBucket
   *   Cloud Storage bucket for staging batch inputs and outputs.
   * @param gcsPathPrefix
   *   Object-name prefix under which the batch folders are created.
   */
  def asOpenAIWithBatchSupport(
    gcsBucket: String,
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey),
    gcsPathPrefix: String = "openai-scala-client-batches",
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService =
    new OpenAIVertexAIChatCompletionService(
      VertexAIServiceFactory(projectId, location),
      batchSupport = Some(
        VertexAIBatchSupport(
          batchService = batchPrediction(projectId, location, timeouts),
          gcsBucket = gcsBucket,
          gcsPathPrefix = gcsPathPrefix
        )
      )
    )

  /**
   * Create a new instance of the [[VertexAIBatchPredictionService]] - a REST-based client for
   * Vertex AI batch prediction jobs (Gemini models, 50% of standard cost). Authenticates with
   * Google Application Default Credentials (`gcloud auth application-default login`, or a
   * service account via `GOOGLE_APPLICATION_CREDENTIALS`).
   *
   * @param projectId
   *   GCP project id (defaults to the VERTEXAI_PROJECT_ID env. variable)
   * @param location
   *   GCP location, e.g. `us-central1` or `global` (defaults to the VERTEXAI_LOCATION env.
   *   variable)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   */
  def batchPrediction(
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): VertexAIBatchPredictionService =
    new VertexAIBatchPredictionServiceImpl(projectId, location, timeouts)

  /**
   * [[batchPrediction]] variant on a CALLER-SUPPLIED, SITE-STATELESS engine - e.g. one shared
   * with other providers/services via a single connection pool and actor system. The site
   * binding (project/location-shaped base URL, per-request ADC Bearer auth, logging label) is
   * built internally from `projectId`/`location`. Closing the returned service does NOT close
   * the shared engine - close the engine once, when done with all services using it.
   *
   * @param projectId
   *   GCP project id (defaults to the VERTEXAI_PROJECT_ID env. variable)
   * @param location
   *   GCP location, e.g. `us-central1` or `global` (defaults to the VERTEXAI_LOCATION env.
   *   variable)
   */
  def batchPredictionWithEngine(
    engine: WSClientEngine,
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey)
  )(
    implicit ec: ExecutionContext
  ): VertexAIBatchPredictionService =
    new VertexAIBatchPredictionServiceImpl(
      projectId,
      location,
      externalEngine = Some(engine)
    )
}
