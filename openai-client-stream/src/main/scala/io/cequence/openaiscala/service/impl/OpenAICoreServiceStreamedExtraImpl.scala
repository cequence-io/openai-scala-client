package io.cequence.openaiscala.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.responsesapi.JsonFormats.{
  createModelResponseSettingsFormat,
  inputsWrites,
  responseStreamEventReads
}
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ResponseStreamEvent
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceExtra
import io.cequence.wsclient.JsonUtil.JsonOps
import play.api.libs.json.{JsValue, Json}

/**
 * Private impl. class of [[OpenAIStreamedServiceExtra]] which offers extra functions with
 * streaming support.
 *
 * @since Jan
 *   2023
 */
private[service] trait OpenAICoreServiceStreamedExtraImpl
    extends OpenAIStreamedServiceExtra
    with OpenAIChatCompletionServiceStreamedExtraImpl
    with CompletionBodyMaker {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createCompletionStreamed(
    prompt: String,
    settings: CreateCompletionSettings
  ): Source[TextCompletionResponse, NotUsed] =
    execJsonStream(
      EndPoint.completions,
      "POST",
      bodyParams = createBodyParamsForCompletion(prompt, settings, stream = true),
      maxFrameLength = Some(OpenAIChatCompletionServiceStreamedExtraImpl.maxFrameLength)
    ).map { (json: JsValue) =>
      inBandStreamError(json).foreach(throw _)
      json.asSafe[TextCompletionResponse]
    }

  override def createModelResponseStreamed(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): Source[ResponseStreamEvent, NotUsed] = {
    val body =
      Json.toJsObject(settings.copy(stream = Some(true)))(createModelResponseSettingsFormat) ++
        Json.obj("input" -> inputsWrites.writes(inputs))

    execJsonStream(
      EndPoint.responses,
      "POST",
      bodyParams = body.fields.toList.map { case (name, value) =>
        Param.Raw(name) -> Some(value)
      },
      maxFrameLength = Some(OpenAIChatCompletionServiceStreamedExtraImpl.maxFrameLength)
    ).map { (json: JsValue) =>
      // an error body ({"error": {...}}); streamed `error` events carry the message at the
      // top level and are modeled as ResponseStreamEvent.ErrorEvent
      inBandStreamError(json).foreach(throw _)
      json.asSafe[ResponseStreamEvent]
    }
  }
}
