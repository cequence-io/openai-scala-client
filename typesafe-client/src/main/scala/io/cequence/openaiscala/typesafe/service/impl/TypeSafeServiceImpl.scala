package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain.{
  DecisionImage,
  ModelMetadata,
  Question,
  SystemOneRequest,
  SystemOneResponse,
  TypeSafeModelId
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
 * @param api
 *   what the host does differently - the endpoint, the model listing, images
 */
private[service] class TypeSafeServiceImpl(
  apiKey: String,
  baseUrl: String,
  override val defaultModel: String,
  timeouts: Option[Timeouts] = None,
  externalEngine: Option[WSClientEngine] = None,
  api: DecisionApi = DecisionApi.typeSafe
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
      label = Some(api.label)
    )

  override def systemOne(
    state: JsValue,
    questions: Map[String, Question],
    model: String
  ): Future[SystemOneResponse] = {
    // fails fast (IllegalArgumentException) before any I/O
    val request = SystemOneRequest(state, model, questions)

    api.maxQuestions.foreach { max =>
      require(
        questions.size <= max,
        s"${api.label} takes at most $max questions per request (got ${questions.size}) - " +
          "split them over several requests."
      )
    }

    if (api.images) {
      val problems = DecisionImage.problems(state)
      require(
        problems.isEmpty,
        s"The state carries an image the API cannot take: ${problems.mkString("; ")}."
      )
    }

    execPOSTBodyRich(
      api.decisions,
      body = Json.toJson(request)
    ).map { rich =>
      val response = responseOrError(rich).json.asSafe[SystemOneResponse]
      response.copy(requestId = TypeSafeServiceImpl.requestId(rich))
    }.recoverWith(transportErrors)
  }

  override def listModels: Future[Seq[ModelMetadata]] =
    api.models.fold(
      execGETRich(EndPoint.models).map { rich =>
        (responseOrError(rich).json \ "models").get.asSafe[Seq[ModelMetadata]]
      }.recoverWith(transportErrors)
    )(Future.successful)

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

  // TypeSafe's own header first, then the `x-request-id` of Perplexity (and most other hosts) -
  // by priority, not by their order in the response
  private val requestIdHeaders =
    Seq(TypeSafeServiceConsts.requestIdHeader, TypeSafeServiceConsts.genericRequestIdHeader)

  private[impl] def requestId(rich: RichResponse): Option[String] =
    requestIdHeaders.map { header =>
      rich.headers.collectFirst {
        case (name, values) if name.equalsIgnoreCase(header) => values.headOption
      }.flatten
    }.collectFirst { case Some(id) => id }
}

/**
 * What differs between the hosts of the System One question / answer format.
 *
 * @param label
 *   the site's label (logs)
 * @param decisions
 *   the endpoint that answers the questions
 * @param models
 *   the models [[TypeSafeServiceImpl.listModels]] returns without a request, for a host that
 *   lists none of its own
 * @param images
 *   whether the host reads image parts in the state - their URLs and sizes are then checked
 *   before sending ([[DecisionImage]])
 * @param maxQuestions
 *   the most questions the host takes per request, checked before sending
 */
private[service] final case class DecisionApi(
  label: String,
  decisions: EndPoint,
  models: Option[Seq[ModelMetadata]] = None,
  images: Boolean = false,
  maxQuestions: Option[Int] = None
)

private[service] object DecisionApi {

  // Jev took 129 questions in one request (live 2026-10-02) - no cap known
  val typeSafe: DecisionApi = DecisionApi("typesafe", EndPoint.systemOne)

  // 422 "A request accepts at most 128 questions." (live 2026-10-02)
  val liquid: DecisionApi = DecisionApi("liquid", EndPoint.systemOne, maxQuestions = Some(128))

  // Perplexity's `GET /v1/models` lists its Agent API models, not the decider (live
  // 2026-10-02); the Decisions API has this one model
  val perplexity: DecisionApi = DecisionApi(
    "perplexity",
    EndPoint.decisions,
    models = Some(
      Seq(
        ModelMetadata(
          TypeSafeModelId.pplx_decider_v1_27b,
          "Perplexity's multimodal decision model, the one model of its Decisions API.",
          "2026-10-01"
        )
      )
    ),
    images = true,
    // 400 "Each request needs between 1 and 128 questions" (live 2026-10-02)
    maxQuestions = Some(128)
  )
}
