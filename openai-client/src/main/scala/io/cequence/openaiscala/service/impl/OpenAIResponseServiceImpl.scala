package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.SortOrder
import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import io.cequence.wsclient.ResponseImplicits._
import io.cequence.wsclient.stream.AsyncCall
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  GetInputTokensCountSettings,
  InputItemsResponse,
  InputTokensCount,
  Inputs,
  Response,
  DeleteResponse => ResponsesAPIDeleteResponse
}
import io.cequence.openaiscala.service.{OpenAIResponsesService, PathSegment}
import play.api.libs.json.Json

import scala.concurrent.Future

trait OpenAIResponseServiceImpl extends OpenAIResponsesService with OpenAIServiceWSBase {

  override def createModelResponse(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): Future[Response] =
    createModelResponseCall(inputs, settings).toFuture

  // native: the engine's own call; the body is built per run
  override def createModelResponseCall(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): AsyncCall[Response] =
    AsyncCall.defer {
      val input = inputsWrites.writes(inputs)
      val body = Json.toJsObject(settings)(createModelResponseSettingsFormat)

      execPOSTBodyCall(
        EndPoint.responses,
        body = body ++ Json.obj("input" -> input),
        extraHeaders = CreateModelResponseSettings.betaHeaders(settings)
      ).map(_.asSafeJson[Response])
    }

  override def getModelResponse(
    responseId: String,
    include: Seq[String]
  ): Future[Response] =
    getModelResponseCall(responseId, include).toFuture

  // a bad id fails the call (PathSegment), never a synchronous throw
  override def getModelResponseCall(
    responseId: String,
    include: Seq[String]
  ): AsyncCall[Response] =
    AsyncCall.defer {
      execGETCall(
        EndPoint.responses,
        endPointParam = Some(PathSegment(responseId)),
        params = Seq(
          Param.include -> (if (include.nonEmpty) Some(include) else None)
        )
      ).map(_.asSafeJson[Response])
    }

  override def deleteModelResponse(
    responseId: String
  ): Future[ResponsesAPIDeleteResponse] =
    PathSegment.inFuture {
      execDELETE(
        EndPoint.responses,
        endPointParam = Some(PathSegment(responseId))
      ).map(_.asSafeJson[ResponsesAPIDeleteResponse])
    }

  override def cancelModelResponse(
    responseId: String
  ): Future[Response] =
    PathSegment.inFuture {
      execPOST(
        EndPoint.responses,
        Some(s"${PathSegment(responseId)}/cancel")
      ).map(_.asSafeJson[Response])
    }

  override def getModelResponseInputTokenCounts(
    inputs: Inputs,
    settings: GetInputTokensCountSettings
  ): Future[InputTokensCount] = {
    val input = inputsWrites.writes(inputs)
    val body = Json.toJsObject(settings)(getInputTokensCountSettingsFormat)

    execPOSTBody(
      EndPoint.responses,
      endPointParam = Some("input_tokens"),
      body = body ++ Json.obj("input" -> input)
    ).map(_.asSafeJson[InputTokensCount])
  }

  override def listModelResponseInputItems(
    responseId: String,
    after: Option[String] = None,
    before: Option[String] = None,
    include: Seq[String] = Nil,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None
  ): Future[InputItemsResponse] =
    PathSegment.inFuture {
      execGET(
        EndPoint.responses,
        endPointParam = Some(s"${PathSegment(responseId)}/input_items"),
        params = Seq(
          Param.after -> after,
          Param.before -> before,
          Param.include -> (if (include.nonEmpty) Some(include) else None),
          Param.limit -> limit,
          Param.order -> order
        )
      ).map(
        _.asSafeJson[InputItemsResponse]
      )
    }
}
