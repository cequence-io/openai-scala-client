package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.ws.WSRequestHelper
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIServiceConsts, OpenAIWSRequestHelper}
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future

/**
 * Private impl. of [[OpenAIChatCompletionService]].
 *
 * @since Match
 *   2024
 */
private[service] trait OpenAIChatCompletionServiceImpl
    extends OpenAIChatCompletionService
    with OpenAIWSRequestHelper
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
      bodyParams = createBodyParamsForChatCompletion(messages, settings, stream = false)
    ).map(
      _.asSafe[ChatCompletionResponse]
    )
}

trait ChatCompletionBodyMaker {

  this: WSRequestHelper =>

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
      Param.response_format -> settings.response_format_type.map { formatType =>
        Map("type" -> formatType.toString)
      }
    )
  }
}
