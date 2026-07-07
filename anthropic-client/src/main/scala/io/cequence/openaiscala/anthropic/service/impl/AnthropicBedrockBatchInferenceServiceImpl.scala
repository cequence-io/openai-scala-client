package io.cequence.openaiscala.anthropic.service.impl

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.{
  BatchInferenceJob,
  BatchInferenceJobStatus,
  CreateBatchInferenceJobSettings,
  ListBatchInferenceJobsResponse
}
import io.cequence.openaiscala.anthropic.service.AnthropicBedrockBatchInferenceService
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue, WsRequestContext}
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import io.cequence.wsclient.service.ws.{PlayJsonUtil, PlayWSClientEngine, Timeouts}
import io.cequence.wsclient.service.WSClientEngine
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

private[service] sealed abstract class BedrockBatchEndPoint(value: String = "")
    extends NamedEnumValue(value)

private[service] object BedrockBatchEndPoint {
  case object create extends BedrockBatchEndPoint("model-invocation-job")

  case class get(jobId: String) extends BedrockBatchEndPoint(s"model-invocation-job/$jobId")

  case class stop(jobId: String)
      extends BedrockBatchEndPoint(s"model-invocation-job/$jobId/stop")

  case class list(query: String) extends BedrockBatchEndPoint(s"model-invocation-jobs$query")
}

private[service] sealed trait BedrockBatchParam extends EnumValue

/**
 * REST-based impl of [[AnthropicBedrockBatchInferenceService]], hitting the Bedrock
 * control-plane host (`bedrock.{region}.amazonaws.com`) - distinct from the
 * `bedrock-runtime.{region}.amazonaws.com` host used for on-demand invoke. Every request is
 * signed individually with SigV4 (or, if a bearer token is configured, sent as `Authorization:
 * Bearer <token>`), mirroring [[AnthropicBedrockServiceImpl]]'s auth handling.
 */
private[service] class AnthropicBedrockBatchInferenceServiceImpl(
  connectionInfo: BedrockConnectionSettings,
  explTimeouts: Option[Timeouts] = None
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends AnthropicBedrockBatchInferenceService
    with WSClientWithEngine
    with BedrockAuthHelper
    with BedrockBatchJsonFormats {

  override protected type PEP = BedrockBatchEndPoint
  override protected type PT = BedrockBatchParam

  private val serviceName = "bedrock"

  override protected val engine: WSClientEngine =
    PlayWSClientEngine(
      coreUrl = s"https://bedrock.${connectionInfo.region}.amazonaws.com/",
      WsRequestContext(explTimeouts = explTimeouts)
    )

  private def signedHeaders(
    method: String,
    endpointPath: String,
    body: String
  ): Seq[(String, String)] =
    connectionInfo.bearerToken match {
      case Some(token) =>
        Seq("Authorization" -> s"Bearer $token")

      case None =>
        addAuthHeaders(
          method,
          createURL(Some(endpointPath)),
          headers = Map.empty,
          body = body,
          accessKey = connectionInfo.accessKey,
          secretKey = connectionInfo.secretKey,
          region = connectionInfo.region,
          service = serviceName,
          sessionToken = connectionInfo.sessionToken
        ).toSeq
    }

  override def createBatchInferenceJob(
    settings: CreateBatchInferenceJobSettings
  ): Future[BatchInferenceJob] = {
    val body = Json.obj(
      "jobName" -> settings.jobName,
      "modelId" -> settings.modelId,
      "roleArn" -> settings.roleArn,
      "inputDataConfig" -> Json.obj(
        "s3InputDataConfig" -> Json.obj("s3Uri" -> settings.inputS3Uri)
      ),
      "outputDataConfig" -> Json.obj(
        "s3OutputDataConfig" -> Json.obj("s3Uri" -> settings.outputS3Uri)
      )
    ) ++ settings.timeoutDurationInHours
      .map(hours => Json.obj("timeoutDurationInHours" -> hours))
      .getOrElse(Json.obj())

    val endpoint = BedrockBatchEndPoint.create
    val extraHeaders =
      signedHeaders("POST", endpoint.toString, PlayJsonUtil.wsClientStringify(body))

    execPOSTBody(endpoint, body = body, extraHeaders = extraHeaders).map { response =>
      val jobArn = (response.asSafeJson[JsValue] \ "jobArn").as[String]

      BatchInferenceJob(
        jobArn = jobArn,
        jobName = Some(settings.jobName),
        modelId = Some(settings.modelId),
        status = Some(BatchInferenceJobStatus.Submitted),
        inputS3Uri = Some(settings.inputS3Uri),
        outputS3Uri = Some(settings.outputS3Uri),
        roleArn = Some(settings.roleArn)
      )
    }
  }

  override def getBatchInferenceJob(jobIdentifier: String): Future[BatchInferenceJob] = {
    val encodedId = encodeOnce(jobIdentifier)
    val endpoint = BedrockBatchEndPoint.get(encodedId)
    // the canonical request signed for SigV4 needs the path segment doubly URI-encoded - see
    // the scaladoc on encodeOnce below
    val extraHeaders =
      signedHeaders("GET", s"model-invocation-job/${encodeOnce(encodedId)}", "")

    execGET(endpoint, extraHeaders = extraHeaders).map(_.asSafeJson[BatchInferenceJob])
  }

  override def listBatchInferenceJobs(
    maxResults: Option[Int],
    nextToken: Option[String],
    statusEquals: Option[BatchInferenceJobStatus],
    nameContains: Option[String]
  ): Future[ListBatchInferenceJobsResponse] = {
    val queryParams = Seq(
      "maxResults" -> maxResults.map(_.toString),
      "nextToken" -> nextToken,
      "statusEquals" -> statusEquals.map(_.toString),
      "nameContains" -> nameContains
    ).collect { case (key, Some(v)) => s"$key=${rfc3986Encode(v)}" }

    val query = if (queryParams.nonEmpty) s"?${queryParams.mkString("&")}" else ""

    val endpoint = BedrockBatchEndPoint.list(query)
    val extraHeaders = signedHeaders("GET", endpoint.toString, "")

    execGET(endpoint, extraHeaders = extraHeaders)
      .map(_.asSafeJson[ListBatchInferenceJobsResponse])
  }

  override def stopBatchInferenceJob(jobIdentifier: String): Future[Unit] = {
    val encodedId = encodeOnce(jobIdentifier)
    val endpoint = BedrockBatchEndPoint.stop(encodedId)
    val extraHeaders =
      signedHeaders("POST", s"model-invocation-job/${encodeOnce(encodedId)}/stop", "")

    execPOST(endpoint, extraHeaders = extraHeaders).map(_ => ())
  }

  // Get/Stop's jobIdentifier pattern documents that a bare 12-char id is accepted alongside the
  // full ARN - live-verified that only the full ARN actually works. The ARN's colons/slashes
  // must be percent-encoded once to form a single opaque path segment for the real request, and
  // encoded a second time for the SigV4 canonical request the signature is computed over (a
  // standard SigV4 requirement whenever a path segment itself contains encoded reserved chars,
  // e.g. S3 object keys with slashes).
  private def encodeOnce(value: String): String = rfc3986Encode(value)

  override def close(): Unit = engine.close()
}
