package io.cequence.openaiscala.perplexity.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.perplexity.AgentJsonFormats
import io.cequence.openaiscala.perplexity.AgentJsonFormats._
import io.cequence.openaiscala.perplexity.domain.agent._
import io.cequence.openaiscala.perplexity.domain.Message
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings
import io.cequence.openaiscala.perplexity.JsonFormats._
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.openaiscala.perplexity.service.{
  HandlePerplexityErrorCodes,
  PerplexityScalaClientException,
  PerplexityScalaClientTimeoutException,
  PerplexityScalaClientUnknownHostException,
  SonarService
}
import io.cequence.openaiscala.service.StreamingConsts
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.StreamResponseImplicits.StreamSafeOps
import io.cequence.wsclient.domain.{
  CequenceWSTimeoutException,
  CequenceWSUnknownHostException,
  Response,
  RichResponse,
  SiteBinding,
  WsRequestContext
}
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}
import io.cequence.wsclient.service.WSClientWithEngineStreamTypes.WSClientWithOutputStreamEngine
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import play.api.libs.json._

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

private[service] class SonarServiceImpl(
  apiKey: String,
  externalEngine: Option[WSClientEngine with WSClientOutputStreamExtraAkka] = None,
  baseUrl: Option[String] = None
)(
  override implicit val ec: ExecutionContext
) extends SonarService
    with WSClientWithOutputStreamEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  // a caller-supplied, site-stateless engine (e.g. one shared with other providers), or a
  // privately-owned classpath-discovered engine with output streaming (SSE) support
  override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
    externalEngine.getOrElse(
      StreamedEngineRegistry.outputStreamed(TransportSettings())
    )

  // the engine is only closed by this service when it was privately created here - a
  // caller-supplied engine is closed by its creator
  override protected def ownsEngine: Boolean = externalEngine.isEmpty

  override protected val site: SiteBinding =
    SiteBinding(
      baseUrl.getOrElse(coreUrl),
      WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer ${apiKey}"))),
      label = Some("sonar")
    )

  @deprecated(
    "Perplexity ends support for the Sonar chat completions API on 2026-09-27 - use the Agent API: createAgentResponse / createAgentResponseStreamed",
    "1.3.1"
  )
  override def createChatCompletion(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings
  ): Future[SonarChatCompletionResponse] =
    execPOST(
      EndPoint.chatCompletion,
      bodyParams = createBodyParamsForChatCompletion(messages, settings, stream = false)
    ).map(
      _.asSafeJson[SonarChatCompletionResponse]
    )

  @deprecated(
    "Perplexity ends support for the Sonar chat completions API on 2026-09-27 - use the Agent API: createAgentResponse / createAgentResponseStreamed",
    "1.3.1"
  )
  override def createChatCompletionStreamed(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings
  ): Source[SonarChatCompletionChunkResponse, NotUsed] = {
    val bodyParams =
      createBodyParamsForChatCompletion(messages, settings, stream = true)
    val stringParams = paramTuplesToStrings(bodyParams)

    engine
      .execJsonStream(
        site,
        EndPoint.chatCompletion.toString(),
        "POST",
        bodyParams = stringParams,
        framingDelimiter = "\r\n\r\n",
        // citation-heavy frames exceed ws-client's 20 000-byte default
        maxFrameLength = Some(StreamingConsts.DefaultMaxFrameLength)
      )
      .map { json =>
        (json \ "error").toOption.map { error =>
          throw new OpenAIScalaClientException(error.toString())
        }.getOrElse {
          json.asSafe[SonarChatCompletionChunkResponse]
        }
      }
  }

  // every other call (the Sonar chat completions) goes through ws-client's error hook
  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing =
    throw HandlePerplexityErrorCodes.toException(httpCode, message)

  // ---- Agent API ----

  override def createAgentResponse(
    input: AgentInput,
    settings: CreateAgentResponseSettings
  ): Future[AgentResponse] =
    execPOSTRich(
      EndPoint.agent,
      bodyParams = agentBodyParams(input, settings, stream = false)
    ).map(responseOrError(_).asSafeJson[AgentResponse]).recoverWith(transportErrors)

  override def createAgentResponseStreamed(
    input: AgentInput,
    settings: CreateAgentResponseSettings
  ): Source[AgentStreamEvent, NotUsed] =
    agentEvents(
      engine.execRawStream(
        site,
        EndPoint.agent.toString(),
        "POST",
        endPointParam = None,
        params = Nil,
        bodyParams = paramTuplesToStrings(agentBodyParams(input, settings, stream = true)),
        extraHeaders = Seq("Accept" -> "text/event-stream")
      )
    )

  override def retrieveAgentResponse(responseId: String): Future[Option[AgentResponse]] =
    execGETRich(
      EndPoint.agent,
      endPointParam = Some(responseId)
    ).map(responseOrNone(_).map(_.asSafeJson[AgentResponse])).recoverWith(transportErrors)

  override def resumeAgentResponseStream(
    responseId: String,
    startingAfter: Option[Int]
  ): Source[AgentStreamEvent, NotUsed] =
    agentEvents(
      engine.execRawStream(
        site,
        EndPoint.agent.toString(),
        "GET",
        endPointParam = Some(responseId),
        params = Seq(
          "stream" -> Some("true"),
          "starting_after" -> startingAfter.map(_.toString)
        ),
        bodyParams = Nil,
        extraHeaders = Seq("Accept" -> "text/event-stream")
      )
    )

  override def cancelAgentResponse(responseId: String): Future[AgentCancelResponse] =
    execPOSTRich(
      EndPoint.agent,
      endPointParam = Some(s"$responseId/cancel")
    ).map(responseOrError(_).asSafeJson[AgentCancelResponse]).recoverWith(transportErrors)

  override def listAgentResponseFiles(responseId: String): Future[Seq[AgentResponseFile]] =
    execGETRich(
      EndPoint.agent,
      endPointParam = Some(s"$responseId/files")
    ).map(rich =>
      dataOf[AgentResponseFile](responseOrError(rich), s"v1/agent/$responseId/files")
    ).recoverWith(transportErrors)

  override def downloadAgentResponseFile(
    responseId: String,
    fileId: String
  ): Future[Option[Source[ByteString, _]]] =
    execGETRich(
      EndPoint.agent,
      endPointParam = Some(s"$responseId/files/$fileId/content")
    ).map(responseOrNone(_).map(_.asSafeSource)).recoverWith(transportErrors)

  override def listAgentModels: Future[Seq[AgentModel]] =
    execGETRich(EndPoint.models)
      .map(rich => dataOf[AgentModel](responseOrError(rich), "v1/models"))
      .recoverWith(transportErrors)

  private def dataOf[T: Reads](
    response: Response,
    path: String
  ): Seq[T] =
    (response.asSafeJson[JsObject] \ "data").validate[Seq[T]] match {
      case JsSuccess(items, _) => items
      case JsError(errors) =>
        throw new PerplexityScalaClientException(s"Unexpected response of GET $path: $errors")
    }

  // like ws-client's getResponseOrError, but the exception also carries the request id
  private def responseOrError(rich: RichResponse): Response =
    rich.response.getOrElse(
      throw HandlePerplexityErrorCodes.toException(
        rich.status.code,
        rich.status.message,
        SonarServiceImpl.requestId(rich)
      )
    )

  private def responseOrNone(rich: RichResponse): Option[Response] =
    if (rich.status.code == 404) None else Some(responseOrError(rich))

  private def transportErrors[T]: PartialFunction[Throwable, Future[T]] = {
    case e @ (_: CequenceWSTimeoutException | _: TimeoutException) =>
      Future.failed(new PerplexityScalaClientTimeoutException(e.getMessage, e))
    case e @ (_: CequenceWSUnknownHostException | _: UnknownHostException) =>
      Future.failed(new PerplexityScalaClientUnknownHostException(e.getMessage, e))
  }

  private def agentBodyParams(
    input: AgentInput,
    settings: CreateAgentResponseSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] =
    AgentJsonFormats.createAgentRequestBody(input, settings, stream).fields.toList.map {
      case (name, value) => Param.Raw(name) -> Some(value)
    }

  // SSE bytes -> typed events; an `{"error": ...}` payload - the error body answering the
  // request (its `code` is the HTTP status, which itself does not reach a raw stream) - fails
  // the stream with the classified exception
  private def agentEvents(bytes: Source[ByteString, NotUsed])
    : Source[AgentStreamEvent, NotUsed] =
    bytes.via(ServerSentEvents.jsonPayloads()).map { json =>
      if ((json \ "type").isEmpty && (json \ "error").isDefined)
        throw HandlePerplexityErrorCodes
          .fromErrorBody(json)
          .getOrElse(new PerplexityScalaClientException(json.toString()))
      else
        json.asSafe[AgentStreamEvent]
    }

  private def createBodyParamsForChatCompletion(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")

    jsonBodyParams(
      Param.messages -> Some(Json.toJson(messages)),
      Param.model -> Some(settings.model),
      Param.frequency_penalty -> settings.frequency_penalty,
      Param.max_tokens -> settings.max_tokens,
      Param.presence_penalty -> settings.presence_penalty,
      Param.response_format -> settings.response_format.map(Json.toJson(_)),
      Param.return_images -> settings.return_images,
      Param.return_related_questions -> settings.return_related_questions,
      Param.search_domain_filter -> (if (settings.search_domain_filter.nonEmpty)
                                       Some(settings.search_domain_filter)
                                     else None),
      Param.search_recency_filter -> settings.search_recency_filter.map(_.toString()),
      Param.stream -> Some(stream),
      Param.temperature -> settings.temperature,
      Param.top_k -> settings.top_k,
      Param.top_p -> settings.top_p
    )
  }
}

private[service] object SonarServiceImpl {

  val requestIdHeader = "x-request-id"

  def requestId(rich: RichResponse): Option[String] =
    rich.headers.collectFirst {
      case (name, values) if name.equalsIgnoreCase(requestIdHeader) => values.headOption
    }.flatten
}
