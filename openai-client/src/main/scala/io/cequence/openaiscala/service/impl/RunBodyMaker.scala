package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateRunSettings
}
import io.cequence.wsclient.service.ws.WSRequestHelper
import play.api.libs.json.{JsValue, Json}

trait RunBodyMaker {

  this: WSRequestHelper =>

  protected def createBodyParamsForRun(
    settings: CreateRunSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {

    jsonBodyParams(
      Param.model -> Some(settings.model),
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.topP,
      Param.stream -> Some(stream),
      Param.max_prompt_tokens -> settings.maxPromptTokens,
      Param.response_format -> settings.responseFormat.map { format =>
        Json.toJson(format)
      }
    )
  }
}
