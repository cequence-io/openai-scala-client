package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.settings.{CreateRunSettings, CreateThreadAndRunSettings}
import io.cequence.wsclient.service.WSClient
import play.api.libs.json.{JsValue, Json}

trait ThreadAndRunBodyMaker {

  this: WSClient =>

  protected def createBodyParamsForThreadAndRun(
    settings: CreateThreadAndRunSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {

    jsonBodyParams(
      Param.model -> settings.model,
      Param.metadata -> (if (settings.metadata.nonEmpty) Some(Json.toJson(settings.metadata))
                         else None),
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.topP,
      Param.max_prompt_tokens -> settings.maxPromptTokens,
      Param.max_completion_tokens -> settings.maxCompletionTokens,
      Param.truncation_strategy -> Some(Json.toJson(settings.truncationStrategy)),
      Param.parallel_tool_calls -> Some(Json.toJson(settings.parallelToolCalls)),
      Param.response_format -> settings.responseFormat.map { format =>
        Json.toJson(format)
      },
      Param.stream -> Some(stream)
    )
  }
}
