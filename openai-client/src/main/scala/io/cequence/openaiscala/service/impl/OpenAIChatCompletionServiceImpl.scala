package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIServiceConsts}
import io.cequence.wsclient.ResponseImplicits._
import io.cequence.wsclient.service.WSClient
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future

/**
 * Private impl. of [[OpenAIChatCompletionService]].
 *
 * @since March
 *   2024
 */
private[service] trait OpenAIChatCompletionServiceImpl
    extends OpenAIChatCompletionService
    with WSClientWithEngine
    with ChatCompletionBodyMaker
    with OpenAIServiceConsts {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    execPOST(
      EndPoint.chat_completions,
      bodyParams =
        createBodyParamsForChatCompletion(messages.toList, settings, stream = false).toList
    ).map(
      _.asSafeJson[ChatCompletionResponse]
    )

}

trait ChatCompletionBodyMaker {

  this: WSClient =>

  protected def createBodyParamsForChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")

    val messageJsons = messages.map(Json.toJson(_)(messageWrites))

    jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> Some(settings.model),
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.top_p,
      Param.n -> settings.n,
      Param.stream -> Some(stream),
      Param.stop -> {
        settings.stop.size match {
          case 0 => None
          case 1 => Some(settings.stop.head)
          case _ => Some(settings.stop)
        }
      },
      Param.max_tokens -> settings.max_tokens,
      Param.presence_penalty -> settings.presence_penalty,
      Param.frequency_penalty -> settings.frequency_penalty,
      Param.logit_bias -> {
        if (settings.logit_bias.isEmpty) None else Some(settings.logit_bias)
      },
      Param.user -> settings.user,
      Param.logprobs -> settings.logprobs,
      Param.top_logprobs -> settings.top_logprobs,
      Param.seed -> settings.seed,
      Param.response_format -> {
        val map =
          settings.response_format_type.map { (formatType: ChatCompletionResponseFormatType) =>
            if (formatType != ChatCompletionResponseFormatType.json_schema)
              Map("type" -> formatType.toString)
            else
              settings.jsonSchema.map { schema =>
                val schema1 = Map(
                  "type" -> "json_schema",
                  "json_schema" -> Map(
                    "name" -> "output_schema", // TODO
                    "schema" -> schema
                  )
                )
                schema1 // ++ (if (settings.strict) Map("strict" -> true) else Map())
              }
          }

        map
      },
      Param.extra_params -> {
        if (settings.extra_params.nonEmpty) Some(settings.extra_params) else None
      }
    )
  }
}
