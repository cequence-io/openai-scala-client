package io.cequence.openaiscala.vertexai.service.impl

import akka.stream.Materializer
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
import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue, WsRequestContext}
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import io.cequence.wsclient.service.ws.{PlayWSClientEngine, Timeouts}
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
private[service] class VertexAIBatchPredictionServiceImpl(
  projectId: String,
  location: String,
  timeouts: Option[Timeouts] = None
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends VertexAIBatchPredictionService
    with WSClientWithEngine
    with JsonFormats {

  override protected type PEP = BatchEndPoint
  override protected type PT = BatchParam

  private val coreUrl = {
    val host =
      if (location == "global") "aiplatform.googleapis.com"
      else s"$location-aiplatform.googleapis.com"

    s"https://$host/v1/projects/$projectId/locations/$location/"
  }

  private lazy val credentials: GoogleCredentials =
    GoogleCredentials.getApplicationDefault.createScoped(
      "https://www.googleapis.com/auth/cloud-platform"
    )

  // the request context is re-evaluated per request, so the token is refreshed when expired
  override protected val engine: WSClientEngine =
    PlayWSClientEngine.withContextFun(
      coreUrl,
      () =>
        WsRequestContext(
          authHeaders = Seq(("Authorization", s"Bearer ${accessToken()}")),
          explTimeouts = timeouts
        )
    )

  private def accessToken(): String =
    blocking {
      credentials.refreshIfExpired()
      credentials.getAccessToken.getTokenValue
    }

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
