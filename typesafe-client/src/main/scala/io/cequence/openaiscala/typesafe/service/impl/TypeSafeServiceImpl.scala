package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain.{
  ModelMetadata,
  Question,
  SystemOneRequest,
  SystemOneResponse
}
import io.cequence.openaiscala.typesafe.service.{
  HandleTypeSafeErrorCodes,
  TypeSafeScalaClientTimeoutException,
  TypeSafeScalaClientUnknownHostException,
  TypeSafeService,
  TypeSafeServiceConsts
}
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.domain.{
  CequenceWSTimeoutException,
  CequenceWSUnknownHostException,
  Response,
  RichResponse,
  SiteBinding,
  WsRequestContext
}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.{JsValue, Json}

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

/**
 * @param baseUrl
 *   the API host (`https://api.typesafe.ai`); the `v1/...` paths are appended here
 * @param timeouts
 *   client-level timeouts for the PRIVATELY-created engine - mutually exclusive with
 *   `externalEngine` (the factory passes one or the other, never both); with a caller-supplied
 *   engine use `engine.copy(TransportSettings(timeouts = ...))` instead
 * @param externalEngine
 *   a caller-supplied, site-stateless engine; not closed by this service
 */
private[service] class TypeSafeServiceImpl(
  apiKey: String,
  baseUrl: String,
  override val defaultModel: String,
  timeouts: Option[Timeouts] = None,
  externalEngine: Option[WSClientEngine] = None
)(
  implicit val ec: ExecutionContext
) extends TypeSafeService
    with WSClientWithEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  // a caller-supplied, site-stateless engine (e.g. one shared with other providers), or a
  // privately-owned classpath-discovered engine
  override protected val engine: WSClientEngine =
    externalEngine.getOrElse(
      WSClientEngineRegistry(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))
    )

  // the engine is only closed by this service when it was privately created here - a
  // caller-supplied engine is closed by its creator
  override protected def ownsEngine: Boolean = externalEngine.isEmpty

  override protected val site: SiteBinding =
    SiteBinding(
      TypeSafeServiceImpl.normalizeBaseUrl(baseUrl),
      WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer ${apiKey}"))),
      label = Some("typesafe")
    )

  override def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] = {
    // fails fast (IllegalArgumentException) before any I/O
    val request = SystemOneRequest(state, model, questions)

    execPOSTBodyRich(
      EndPoint.systemOne,
      body = Json.toJson(request)
    ).map { rich =>
      val response = responseOrError(rich).json.asSafe[SystemOneResponse]
      response.copy(requestId = TypeSafeServiceImpl.requestId(rich))
    }.recoverWith(transportErrors)
  }

  override def listModels: Future[Seq[ModelMetadata]] =
    execGETRich(EndPoint.models).map { rich =>
      (responseOrError(rich).json \ "models").get.asSafe[Seq[ModelMetadata]]
    }.recoverWith(transportErrors)

  // like ws-client's getResponseOrError, but the exception also carries the request id
  private def responseOrError(rich: RichResponse): Response =
    rich.response.getOrElse(
      throw HandleTypeSafeErrorCodes.toException(
        rich.status.code,
        rich.status.message,
        TypeSafeServiceImpl.requestId(rich)
      )
    )

  private def transportErrors[T]: PartialFunction[Throwable, Future[T]] = {
    case e @ (_: CequenceWSTimeoutException | _: TimeoutException) =>
      Future.failed(new TypeSafeScalaClientTimeoutException(e.getMessage, e))
    case e @ (_: CequenceWSUnknownHostException | _: UnknownHostException) =>
      Future.failed(new TypeSafeScalaClientUnknownHostException(e.getMessage, e))
  }
}

private[service] object TypeSafeServiceImpl {

  /**
   * `SiteBinding` joins `coreUrl` and the endpoint verbatim, so the host must end with `/`.
   */
  def normalizeBaseUrl(baseUrl: String): String =
    baseUrl.trim.stripSuffix("/") + "/"

  private[impl] def requestId(rich: RichResponse): Option[String] =
    rich.headers.collectFirst {
      case (name, values) if name.equalsIgnoreCase(TypeSafeServiceConsts.requestIdHeader) =>
        values.headOption
    }.flatten
}
