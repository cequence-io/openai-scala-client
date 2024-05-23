package io.cequence.openaiscala.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtra
import play.api.libs.json.JsValue

/**
 * Private impl. class of [[OpenAIChatCompletionStreamedServiceExtra]] which offers chat
 * completion with streaming support.
 *
 * @since March
 *   2024
 */
private[service] trait OpenAIChatCompletionServiceStreamedExtraImpl
    extends OpenAIChatCompletionStreamedServiceExtra
    with ChatCompletionBodyMaker
    with OpenAIWSStreamRequestHelper {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
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
}
