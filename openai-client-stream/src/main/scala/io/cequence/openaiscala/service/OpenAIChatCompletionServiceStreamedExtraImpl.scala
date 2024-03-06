package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.openaiscala.service.ws.WSStreamRequestHelper
import play.api.libs.json.JsValue

/**
 * Private impl. class of [[OpenAIChatCompletionServiceStreamedExtra]] which offers chat
 * completion with streaming support.
 *
 * @since March
 *   2024
 */
private trait OpenAIChatCompletionServiceStreamedExtraImpl
    extends OpenAIChatCompletionServiceStreamedExtra
    with WSStreamRequestHelper {
  this: OpenAIChatCompletionServiceImpl =>

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
}
