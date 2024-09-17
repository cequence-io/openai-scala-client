package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.{BaseMessage, ModelId}
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.adapter.{
  ChatCompletionSettingsConversions,
  MessageConversions
}
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIServiceConsts}
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.ResponseImplicits._
import io.cequence.wsclient.service.WSClient
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import play.api.libs.json.{JsObject, JsValue, Json}

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

  private val o1Models = Set(
    ModelId.o1_preview,
    ModelId.o1_preview_2024_09_12,
    ModelId.o1_mini,
    ModelId.o1_mini_2024_09_12
  )

  protected def createBodyParamsForChatCompletion(
    messagesAux: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messagesAux.nonEmpty, "At least one message expected.")

    // O1 models needs some special treatment... revisit this later
    val messagesFinal =
      if (o1Models.contains(settings.model))
        MessageConversions.systemToUserMessages(messagesAux)
      else
        messagesAux

    val messageJsons = messagesFinal.map(Json.toJson(_)(messageWrites))

    // O1 models needs some special treatment... revisit this later
    val settingsFinal =
      if (o1Models.contains(settings.model))
        ChatCompletionSettingsConversions.o1Specific(settings)
      else
        settings

    jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> Some(settingsFinal.model),
      Param.temperature -> settingsFinal.temperature,
      Param.top_p -> settingsFinal.top_p,
      Param.n -> settingsFinal.n,
      Param.stream -> Some(stream),
      Param.stop -> {
        settingsFinal.stop.size match {
          case 0 => None
          case 1 => Some(settingsFinal.stop.head)
          case _ => Some(settingsFinal.stop)
        }
      },
      Param.max_tokens -> settingsFinal.max_tokens,
      Param.presence_penalty -> settingsFinal.presence_penalty,
      Param.frequency_penalty -> settingsFinal.frequency_penalty,
      Param.logit_bias -> {
        if (settingsFinal.logit_bias.isEmpty) None else Some(settingsFinal.logit_bias)
      },
      Param.user -> settingsFinal.user,
      Param.logprobs -> settingsFinal.logprobs,
      Param.top_logprobs -> settingsFinal.top_logprobs,
      Param.seed -> settingsFinal.seed,
      Param.response_format -> {
        settingsFinal.response_format_type.map {
          (formatType: ChatCompletionResponseFormatType) =>
            if (formatType != ChatCompletionResponseFormatType.json_schema)
              Map("type" -> formatType.toString)
            else
              handleJsonSchema(settingsFinal)
        }
      },
      Param.extra_params -> {
        if (settingsFinal.extra_params.nonEmpty) Some(settingsFinal.extra_params) else None
      }
    )
  }

  private def handleJsonSchema(
    settings: CreateChatCompletionSettings
  ): Map[String, Any] =
    settings.jsonSchema.map { case JsonSchemaDef(name, strict, structure) =>
      val schemaMap: Map[String, Any] = structure match {
        case Left(schema) =>
          val json = Json.toJson(schema).as[JsObject]
          JsonUtil.toValueMap(json)

        case Right(schema) => schema
      }

      val adjustedSchema: Map[String, Any] = if (strict) {
        // set "additionalProperties" -> false on "object" types if strict
        def addFlagAux(map: Map[String, Any]): Map[String, Any] = {
          val newMap = map.map { case (key, value) =>
            val unwrappedValue = value match {
              case Some(value) => value
              case other       => other
            }

            val newValue = unwrappedValue match {
              case obj: Map[String, Any] =>
                addFlagAux(obj)

              case other =>
                other
            }
            key -> newValue
          }

          if (Seq("object", Some("object")).contains(map.getOrElse("type", ""))) {
            newMap + ("additionalProperties" -> false)
          } else
            newMap
        }

        addFlagAux(schemaMap)
      } else schemaMap

      Map(
        "type" -> "json_schema",
        "json_schema" -> Map(
          "name" -> name,
          "strict" -> strict,
          "schema" -> adjustedSchema
        )
      )
    }.getOrElse(
      // TODO: is it legal?
      Map("type" -> "json_schema")
    )
}
