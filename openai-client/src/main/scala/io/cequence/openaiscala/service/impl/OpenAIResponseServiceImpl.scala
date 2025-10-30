package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.SortOrder
import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import io.cequence.wsclient.ResponseImplicits._
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  GetInputTokensCountSettings,
  InputItemsResponse,
  InputTokensCount,
  Inputs,
  Response,
  DeleteResponse => ResponsesAPIDeleteResponse
}
import io.cequence.openaiscala.service.OpenAIResponsesService
import play.api.libs.json.Json

import scala.concurrent.Future

trait OpenAIResponseServiceImpl extends OpenAIResponsesService with OpenAIServiceWSBase {

  override def createModelResponse(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): Future[Response] = {
    val input = inputsWrites.writes(inputs)
    val body = Json.toJsObject(settings)(createModelResponseSettingsFormat)

    execPOSTBody(
      EndPoint.responses,
      body = body ++ Json.obj("input" -> input)
    ).map(_.asSafeJson[Response])
  }

  override def getModelResponse(
    responseId: String,
    include: Seq[String]
  ): Future[Response] =
    execGET(
      EndPoint.responses,
      endPointParam = Some(responseId),
      params = Seq(
        Param.include -> (if (include.nonEmpty) Some(include) else None)
      )
    ).map(_.asSafeJson[Response])

  override def deleteModelResponse(
    responseId: String
  ): Future[ResponsesAPIDeleteResponse] =
    execDELETE(
      EndPoint.responses,
      endPointParam = Some(responseId)
    ).map(_.asSafeJson[ResponsesAPIDeleteResponse])

  override def cancelModelResponse(
    responseId: String
  ): Future[Response] =
    execPOST(
      EndPoint.responses,
      Some(s"$responseId/cancel")
    ).map(_.asSafeJson[Response])

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
    execGET(
      EndPoint.responses,
      endPointParam = Some(s"$responseId/input_items"),
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
