package io.cequence.openaiscala.vertexai.service.impl

import com.google.auth.oauth2.GoogleCredentials
import io.cequence.openaiscala.vertexai.JsonFormats
import io.cequence.openaiscala.vertexai.domain.{
  BatchJobInput,
  BatchJobOutput,
  BatchPredictionJob,
  CreateBatchPredictionJobSettings,
  ListBatchPredictionJobsResponse
}
import io.cequence.openaiscala.vertexai.service.VertexAIBatchPredictionService
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue, SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.WSClientEngine
import play.api.libs.json.{JsArray, JsString, Json}

import scala.concurrent.{ExecutionContext, Future, blocking}

sealed abstract class BatchEndPoint(value: String = "") extends NamedEnumValue(value)

object BatchEndPoint {
  case object batchPredictionJobs extends BatchEndPoint

  case class batchPredictionJob(jobId: String)
      extends BatchEndPoint(s"batchPredictionJobs/${stripPrefix(jobId)}")

  case class cancelBatchPredictionJob(jobId: String)
      extends BatchEndPoint(s"batchPredictionJobs/${stripPrefix(jobId)}:cancel")

  // accept either a bare job id or a full resource name
  private def stripPrefix(jobId: String): String =
    jobId.split("/").last
}

sealed trait BatchParam extends EnumValue

object BatchParam {
  case object pageSize extends BatchParam
  case object pageToken extends BatchParam
  case object filter extends BatchParam
}

/**
 * REST-based impl of [[VertexAIBatchPredictionService]]. Authenticates with Google Application
 * Default Credentials, whose (auto-refreshed) access token rides as a Bearer auth header on
 * every request.
 */
private[service] object VertexAIBatchPredictionServiceImpl {

  /**
   * The site binding of the Vertex AI batch-prediction REST API - project/location-shaped base
   * URL, per-request Bearer auth via Google Application Default Credentials (refreshed when
   * expired), logging label - shared by both the classpath-discovered and caller-supplied
   * engine paths.
   */
  def siteBinding(
    projectId: String,
    location: String
  ): SiteBinding = {
    val host =
      if (location == "global") "aiplatform.googleapis.com"
      else s"$location-aiplatform.googleapis.com"

    val coreUrl = s"https://$host/v1/projects/$projectId/locations/$location/"

    lazy val credentials: GoogleCredentials =
      GoogleCredentials.getApplicationDefault.createScoped(
        "https://www.googleapis.com/auth/cloud-platform"
      )

    def accessToken(): String =
      blocking {
        credentials.refreshIfExpired()
        credentials.getAccessToken.getTokenValue
      }

    SiteBinding(
      coreUrl,
      requestContextFun = Some(() =>
        WsRequestContext(
          authHeaders = Seq(("Authorization", s"Bearer ${accessToken()}"))
        )
      ),
      label = Some("vertexai-batch")
    )
  }
}

/**
 * @param timeouts
 *   client-level timeouts for the PRIVATELY-created engine - mutually exclusive with
 *   `externalEngine` (the factory passes one or the other, never both); with a caller-supplied
 *   engine use `engine.copy(TransportSettings(timeouts = ...))` instead
 * @param externalEngine
 *   a caller-supplied, site-stateless engine; not closed by this service
 */
private[service] class VertexAIBatchPredictionServiceImpl(
  projectId: String,
  location: String,
  timeouts: Option[Timeouts] = None,
  externalEngine: Option[WSClientEngine] = None
)(
  implicit val ec: ExecutionContext
) extends VertexAIBatchPredictionService
    with WSClientWithEngine
    with JsonFormats {

  override protected type PEP = BatchEndPoint
  override protected type PT = BatchParam

  // a caller-supplied, site-stateless engine (e.g. one shared with other providers), or a
  // privately-owned classpath-discovered engine
  override protected val engine: WSClientEngine =
    externalEngine.getOrElse(
      WSClientEngineRegistry(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))
    )

  // the engine is only closed by this service when it was privately created here - a
  // caller-supplied engine is closed by its creator
  override protected def ownsEngine: Boolean = externalEngine.isEmpty

  // the request context is re-evaluated per request (requestContextFun), so the token is
  // refreshed when expired
  override protected val site: SiteBinding =
    VertexAIBatchPredictionServiceImpl.siteBinding(projectId, location)

  override def createBatchPredictionJob(
    settings: CreateBatchPredictionJobSettings
  ): Future[BatchPredictionJob] = {
    val inputConfigJson = settings.input match {
      case input: BatchJobInput.Gcs =>
        Json.obj(
          "instancesFormat" -> input.instancesFormat,
          "gcsSource" -> Json.obj("uris" -> JsArray(input.uris.map(JsString)))
        )
      case input: BatchJobInput.BigQuery =>
        Json.obj(
          "instancesFormat" -> input.instancesFormat,
          "bigquerySource" -> Json.obj("inputUri" -> input.inputUri)
        )
    }

    val outputConfigJson = settings.output match {
      case output: BatchJobOutput.Gcs =>
        Json.obj(
          "predictionsFormat" -> output.predictionsFormat,
          "gcsDestination" -> Json.obj("outputUriPrefix" -> output.outputUriPrefix)
        )
      case output: BatchJobOutput.BigQuery =>
        Json.obj(
          "predictionsFormat" -> output.predictionsFormat,
          "bigqueryDestination" -> Json.obj("outputUri" -> output.outputUri)
        )
    }

    val body = Json.obj(
      "displayName" -> settings.displayName,
      "model" -> normalizeModel(settings.model),
      "inputConfig" -> inputConfigJson,
      "outputConfig" -> outputConfigJson
    ) ++ (
      if (settings.labels.nonEmpty) Json.obj("labels" -> settings.labels)
      else Json.obj()
    )

    execPOSTBody(
      BatchEndPoint.batchPredictionJobs,
      body = body
    ).map(_.asSafeJson[BatchPredictionJob])
  }

  // a bare model id is expanded to the Google publisher model resource name
  private def normalizeModel(model: String): String =
    if (model.contains("/")) model else s"publishers/google/models/$model"

  override def getBatchPredictionJob(jobId: String): Future[BatchPredictionJob] =
    execGET(
      BatchEndPoint.batchPredictionJob(jobId)
    ).map(_.asSafeJson[BatchPredictionJob])

  override def listBatchPredictionJobs(
    pageSize: Option[Int],
    pageToken: Option[String],
    filter: Option[String]
  ): Future[ListBatchPredictionJobsResponse] =
    execGET(
      BatchEndPoint.batchPredictionJobs,
      params = Seq(
        BatchParam.pageSize -> pageSize,
        BatchParam.pageToken -> pageToken,
        BatchParam.filter -> filter
      )
    ).map(_.asSafeJson[ListBatchPredictionJobsResponse])

  override def cancelBatchPredictionJob(jobId: String): Future[Unit] =
    execPOST(
      BatchEndPoint.cancelBatchPredictionJob(jobId)
    ).map(_ => ())

  override def deleteBatchPredictionJob(jobId: String): Future[Unit] =
    execDELETE(
      BatchEndPoint.batchPredictionJob(jobId)
    ).map(_ => ())
}
