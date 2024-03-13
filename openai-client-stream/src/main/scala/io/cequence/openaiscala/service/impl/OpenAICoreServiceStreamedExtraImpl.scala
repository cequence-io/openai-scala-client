package io.cequence.openaiscala.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.OpenAIStreamedServiceExtra
import play.api.libs.json.JsValue

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
}
