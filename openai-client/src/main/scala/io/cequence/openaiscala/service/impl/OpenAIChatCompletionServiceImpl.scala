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

  private val noSystemMessageModels = Set(
    ModelId.o1_preview,
    ModelId.o1_preview_2024_09_12,
    ModelId.o1_mini,
    ModelId.o1_mini_2024_09_12
  )

  private val o1PreviewModels = Set(
    ModelId.o1_preview,
    ModelId.o1_preview_2024_09_12,
    ModelId.o1_mini,
    ModelId.o1_mini_2024_09_12
  )

  private val regularOModels = Set(
    ModelId.o1,
    ModelId.o1_2024_12_17,
    ModelId.o1_pro,
    ModelId.o1_pro_2025_03_19,
    ModelId.o3,
    ModelId.o3_2025_04_16,
    ModelId.o3_mini,
    ModelId.o3_mini_2025_01_31,
    ModelId.o3_mini_high,
    ModelId.o4_mini,
    ModelId.o4_mini_2025_04_16
  )

  private val gpt5Models = Set(
    ModelId.gpt_5,
    ModelId.gpt_5_2025_08_07,
    ModelId.gpt_5_mini,
    ModelId.gpt_5_mini_2025_08_07,
    ModelId.gpt_5_nano,
    ModelId.gpt_5_nano_2025_08_07,
    ModelId.gpt_5_chat_latest
  )

  protected def createBodyParamsForChatCompletion(
    messagesAux: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messagesAux.nonEmpty, "At least one message expected.")

    // O1 models needs some special treatment... revisit this later
    val messagesFinal =
      if (noSystemMessageModels.contains(settings.model))
        MessageConversions.systemToUserMessages(messagesAux)
      else
        messagesAux

    val messageJsons = messagesFinal.map(Json.toJson(_)(messageWrites))

    // revisit this later
    val settingsFinal =
      if (o1PreviewModels.contains(settings.model))
        ChatCompletionSettingsConversions.o1Preview(settings)
      else if (regularOModels.contains(settings.model))
        ChatCompletionSettingsConversions.o(settings)
      else if (gpt5Models.contains(settings.model))
        ChatCompletionSettingsConversions.gpt5(settings)
      else
        settings

    JsonUtil.jsonBodyParams(
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
      Param.parallel_tool_calls -> settingsFinal.parallel_tool_calls,
      Param.store -> settingsFinal.store,
      Param.reasoning_effort -> settingsFinal.reasoning_effort.map(_.toString()),
      Param.verbosity -> settingsFinal.verbosity.map(_.toString()),
      Param.service_tier -> settingsFinal.service_tier.map(_.toString()),
      Param.metadata -> (if (settingsFinal.metadata.nonEmpty) Some(settingsFinal.metadata)
                         else None),
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
