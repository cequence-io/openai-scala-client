package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.service.ws.WSStreamRequestHelper
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.BaseMessage
import play.api.libs.json.JsValue

/**
 * Private impl. class of [[OpenAIServiceStreamedExtra]] which offers extra functions with
 * streaming support.
 *
 * @since Jan
 *   2023
 */
private trait OpenAIServiceStreamedExtraImpl
    extends OpenAIServiceStreamedExtra
    with WSStreamRequestHelper {
  this: OpenAICoreServiceImpl =>

  override def createCompletionStreamed(
    prompt: String,
    settings: CreateCompletionSettings
  ): Source[TextCompletionResponse, NotUsed] =
    execJsonStreamAux(
      EndPoint.completions,
      "POST",
      bodyParams = createBodyParamsForCompletion(prompt, settings, stream = true)
    ).map { (json: JsValue) =>
      (json \ "error").toOption.map { error =>
        throw new OpenAIScalaClientException(error.toString())
      }.getOrElse(
        json.asSafe[TextCompletionResponse]
      )
    }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    execJsonStreamAux(
      EndPoint.chat_completions,
      "POST",
      bodyParams = createBodyParamsForChatCompletion(messages, settings, stream = true)
    ).map { (json: JsValue) =>
      (json \ "error").toOption.map { error =>
        throw new OpenAIScalaClientException(error.toString())
      }.getOrElse(
        json.asSafe[ChatCompletionChunkResponse]
      )
    }

  override def listFineTuneEventsStreamed(
    fineTuneId: String,
    after: Option[String],
    limit: Option[Int]
  ): Source[FineTuneEvent, NotUsed] =
    execJsonStreamAux(
      EndPoint.fine_tunes,
      "GET",
      endPointParam = Some(s"$fineTuneId/events"),
      params = Seq(
        Param.after -> after,
        Param.limit -> limit,
        Param.stream -> Some(true)
      )
    ).map { json =>
      (json \ "error").toOption.map { error =>
        throw new OpenAIScalaClientException(error.toString())
      }.getOrElse(
        json.asSafe[FineTuneEvent]
      )
    }
}
