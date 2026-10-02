package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain.{
  DecisionImage,
  DecisionModelListing,
  DecisionProvider,
  ModelMetadata,
  Question,
  SystemOneRequest,
  SystemOneResponse
}
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  HandleTypeSafeErrorCodes,
  TypeSafeScalaClientTimeoutException,
  TypeSafeScalaClientUnknownHostException,
  TypeSafeService
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
import play.api.libs.json.{JsObject, JsValue, Json}

import java.net.UnknownHostException
import java.time.{Instant, ZoneOffset}
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
 * @param provider
 *   the host - its decisions path, model listing, question cap, images, request id headers
 *   (the key, base URL and default model come separately, so they can be overridden)
 */
private[service] class TypeSafeServiceImpl(
  apiKey: String,
  baseUrl: String,
  override val defaultModel: String,
  timeouts: Option[Timeouts] = None,
  externalEngine: Option[WSClientEngine] = None,
  provider: DecisionProvider = DecisionProviderSettings.typeSafe
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
      label = Some(provider.label)
    )

  override def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] = {
    // fails fast (IllegalArgumentException) before any I/O
    val request = SystemOneRequest(state, model, questions)

    provider.maxQuestions.foreach { max =>
      require(
        questions.size <= max,
        s"${provider.label} takes at most $max questions per request (got ${questions.size}) - " +
          "split them over several requests."
      )
    }

    if (provider.images) {
      val problems = DecisionImage.problems(state)
      require(
        problems.isEmpty,
        s"The state carries an image the API cannot take: ${problems.mkString("; ")}."
      )
    }

    execPOSTBodyRich(
      EndPoint.custom(provider.decisionsPath),
      body = Json.toJson(request)
    ).map { rich =>
      val response = responseOrError(rich).json.asSafe[SystemOneResponse]
      response.copy(requestId = requestId(rich))
    }.recoverWith(transportErrors)
  }

  override def listModels: Future[Seq[ModelMetadata]] =
    provider.models match {
      case DecisionModelListing.TypeSafe =>
        execGETRich(EndPoint.models).map { rich =>
          (responseOrError(rich).json \ "models").get.asSafe[Seq[ModelMetadata]]
        }.recoverWith(transportErrors)

      case DecisionModelListing.OpenAIStyle(query) =>
        execGETRich(
          EndPoint.models,
          params = query.map { case (name, value) => Param.query(name) -> Some(value) }
        ).map { rich =>
          (responseOrError(rich).json \ "data")
            .asOpt[Seq[JsObject]]
            .getOrElse(Nil)
            .flatMap(TypeSafeServiceImpl.openAIStyleModel)
        }.recoverWith(transportErrors)

      case DecisionModelListing.Fixed(models) =>
        Future.successful(models)
    }

  // like ws-client's getResponseOrError, but the exception also carries the request id
  private def responseOrError(rich: RichResponse): Response =
    rich.response.getOrElse(
      throw HandleTypeSafeErrorCodes.toException(
        rich.status.code,
        rich.status.message,
        requestId(rich)
      )
    )

  // the host's request id headers by priority, not by their order in the response
  private def requestId(rich: RichResponse): Option[String] =
    TypeSafeServiceImpl.requestId(rich, provider.requestIdHeaders)

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

  private[impl] def requestId(
    rich: RichResponse,
    headers: Seq[String]
  ): Option[String] =
    headers.map { header =>
      rich.headers.collectFirst {
        case (name, values) if name.equalsIgnoreCase(header) => values.headOption
      }.flatten
    }.collectFirst { case Some(id) => id }

  // an entry of an OpenAI-style model list (`{"id", "description", "created"}`) as TypeSafe's
  // metadata - the release date from the creation time, if any
  private[impl] def openAIStyleModel(json: JsObject): Option[ModelMetadata] =
    (json \ "id").asOpt[String].map { id =>
      ModelMetadata(
        id,
        (json \ "description").asOpt[String].getOrElse(""),
        (json \ "created")
          .asOpt[Long]
          .map(Instant.ofEpochSecond(_).atZone(ZoneOffset.UTC).toLocalDate.toString)
          .getOrElse("")
      )
    }
}
