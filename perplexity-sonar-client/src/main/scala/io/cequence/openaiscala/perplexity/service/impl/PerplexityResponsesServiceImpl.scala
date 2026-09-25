package io.cequence.openaiscala.perplexity.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala._
import io.cequence.openaiscala.domain.{BaseMessage, SortOrder}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  TextCompletionResponse
}
import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import io.cequence.openaiscala.domain.responsesapi._
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateCompletionSettings
}
import io.cequence.openaiscala.perplexity.service._
import io.cequence.openaiscala.service.{OpenAIResponsesService, OpenAIStreamedServiceExtra}
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.domain.{
  CequenceWSTimeoutException,
  CequenceWSUnknownHostException,
  SiteBinding,
  WsRequestContext
}
import io.cequence.wsclient.service.WSClientWithEngineStreamTypes.WSClientWithOutputStreamEngine
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}
import play.api.libs.json.{JsObject, Json}

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

/**
 * The OpenAI Responses API surface of Perplexity's Agent API - `POST /v1/responses` is
 * Perplexity's alias of `/v1/agent` for OpenAI SDK compatibility - backing
 * `SonarServiceFactory.agentAsOpenAI` (the core Responses chat adapter runs on top of it). It
 * speaks the core Responses domain and JSON; create, retrieve and the typed stream are
 * supported, the rest of the OpenAI Responses API is not. Errors are classified as
 * [[PerplexityScalaClientException]]s and repacked onto the shared `OpenAIScala*` types (the
 * Perplexity exception - with its `errorType` / `requestId` - as the cause).
 */
private[service] class PerplexityResponsesServiceImpl(
  apiKey: String,
  baseUrl: String,
  externalEngine: Option[WSClientEngine with WSClientOutputStreamExtraAkka] = None
)(
  override implicit val ec: ExecutionContext
) extends OpenAIResponsesService
    with OpenAIStreamedServiceExtra
    with WSClientWithOutputStreamEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
    externalEngine.getOrElse(StreamedEngineRegistry.outputStreamed(TransportSettings()))

  override protected def ownsEngine: Boolean = externalEngine.isEmpty

  override protected val site: SiteBinding =
    SiteBinding(
      baseUrl,
      WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer $apiKey"))),
      label = Some("perplexity-responses")
    )

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing =
    throw PerplexityResponsesServiceImpl.toOpenAIException(
      HandlePerplexityErrorCodes.toException(httpCode, message)
    )

  override def createModelResponse(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): Future[Response] =
    execPOSTBody(
      EndPoint.responses,
      body = requestBody(inputs, settings, stream = false)
    ).map(_.asSafeJson[Response]).recoverWith(transportErrors)

  override def getModelResponse(
    responseId: String,
    include: Seq[String]
  ): Future[Response] =
    execGET(EndPoint.responses, endPointParam = Some(responseId))
      .map(_.asSafeJson[Response])
      .recoverWith(transportErrors)

  override def createModelResponseStreamed(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): Source[ResponseStreamEvent, NotUsed] =
    engine
      .execRawStream(
        site,
        EndPoint.responses.toString(),
        "POST",
        endPointParam = None,
        params = Nil,
        bodyParams = requestBody(inputs, settings, stream = true).fields.toList.map {
          case (name, value) => name -> Some(value)
        },
        extraHeaders = Seq("Accept" -> "text/event-stream")
      )
      .via(ServerSentEvents.jsonPayloads())
      .map { json =>
        if ((json \ "type").isEmpty && (json \ "error").isDefined)
          throw PerplexityResponsesServiceImpl.toOpenAIException(
            HandlePerplexityErrorCodes
              .fromErrorBody(json)
              .getOrElse(new PerplexityScalaClientException(json.toString()))
          )
        else
          json.asSafe[ResponseStreamEvent]
      }

  private def requestBody(
    inputs: Inputs,
    settings: CreateModelResponseSettings,
    stream: Boolean
  ): JsObject =
    Json.toJsObject(settings.copy(stream = Some(stream)))(createModelResponseSettingsFormat) ++
      Json.obj("input" -> inputsWrites.writes(inputs))

  private def transportErrors[T]: PartialFunction[Throwable, Future[T]] = {
    case e @ (_: CequenceWSTimeoutException | _: TimeoutException) =>
      Future.failed(new OpenAIScalaClientTimeoutException(e.getMessage, e))
    case e @ (_: CequenceWSUnknownHostException | _: UnknownHostException) =>
      Future.failed(new OpenAIScalaClientUnknownHostException(e.getMessage, e))
  }

  // ---- not offered by Perplexity's Responses alias ----

  private def unsupported(what: String) =
    new OpenAIScalaClientException(
      s"$what is not supported by Perplexity's Agent API (OpenAI Responses alias) - use SonarService for its native operations."
    )

  override def deleteModelResponse(responseId: String): Future[DeleteResponse] =
    Future.failed(unsupported("deleteModelResponse"))

  override def cancelModelResponse(responseId: String): Future[Response] =
    Future.failed(unsupported("cancelModelResponse (use SonarService.cancelAgentResponse)"))

  override def getModelResponseInputTokenCounts(
    inputs: Inputs,
    settings: GetInputTokensCountSettings
  ): Future[InputTokensCount] =
    Future.failed(unsupported("getModelResponseInputTokenCounts"))

  override def listModelResponseInputItems(
    responseId: String,
    after: Option[String],
    before: Option[String],
    include: Seq[String],
    limit: Option[Int],
    order: Option[SortOrder]
  ): Future[InputItemsResponse] =
    Future.failed(unsupported("listModelResponseInputItems"))

  override def createCompletionStreamed(
    prompt: String,
    settings: CreateCompletionSettings
  ): Source[TextCompletionResponse, NotUsed] =
    Source.failed(unsupported("createCompletionStreamed"))

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    Source.failed(unsupported("createChatCompletionStreamed on the raw Responses service"))
}

private[service] object PerplexityResponsesServiceImpl {

  /** The shared `OpenAIScala*` view of a Perplexity exception (kept as the cause). */
  def toOpenAIException(e: PerplexityScalaClientException): OpenAIScalaClientException =
    e match {
      case _: PerplexityScalaUnauthorizedException =>
        new OpenAIScalaUnauthorizedException(e.getMessage, e)
      case _: PerplexityScalaRateLimitException =>
        new OpenAIScalaRateLimitException(e.getMessage, e)
      case _: PerplexityScalaEngineOverloadedException =>
        new OpenAIScalaEngineOverloadedException(e.getMessage, e)
      case _: PerplexityScalaServerErrorException =>
        new OpenAIScalaServerErrorException(e.getMessage, e)
      case _: PerplexityScalaClientTimeoutException =>
        new OpenAIScalaClientTimeoutException(e.getMessage, e)
      case _: PerplexityScalaClientUnknownHostException =>
        new OpenAIScalaClientUnknownHostException(e.getMessage, e)
      case _ => new OpenAIScalaClientException(e.getMessage, e)
    }
}
