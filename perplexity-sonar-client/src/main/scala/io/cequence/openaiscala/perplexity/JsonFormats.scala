package io.cequence.openaiscala.perplexity

import io.cequence.openaiscala.perplexity.domain.Message.{
  AssistantMessage,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.openaiscala.perplexity.domain.settings.{
  RecencyFilterType,
  SolarResponseFormat,
  SonarCreateChatCompletionSettings
}
import io.cequence.openaiscala.perplexity.domain.{ChatRole, Message}
import io.cequence.openaiscala.JsonFormats.{
  chatCompletionChoiceInfoFormat,
  usageInfoFormat,
  chatCompletionChoiceChunkInfoFormat
}
import io.cequence.wsclient.JsonUtil
import play.api.libs.functional.syntax._
import play.api.libs.json._

object JsonFormats extends JsonFormats

trait JsonFormats {

  implicit lazy val chatRoleFormat: Format[ChatRole] =
    JsonUtil.enumFormat[ChatRole](ChatRole.values: _*)

  implicit lazy val messageWrites: Writes[Message] = (message: Message) =>
    Json.obj(
      "role" -> message.role,
      "content" -> Json.toJson(message.content)
    )

  implicit lazy val messageReads: Reads[Message] = (
    (__ \ "role").read[ChatRole] and
      (__ \ "content").read[String]
  ) {
    (
      role,
      content
    ) =>
      role match {
        case ChatRole.System    => SystemMessage(content)
        case ChatRole.User      => UserMessage(content)
        case ChatRole.Assistant => AssistantMessage(content)
      }
  }

  implicit lazy val messageFormat: Format[Message] = Format(messageReads, messageWrites)

  implicit lazy val solarResponseFormatReads: Reads[SolarResponseFormat] = { (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "json_schema" =>
        (json \ "json_schema" \ "schema")
          .validate[Map[String, Any]](JsonUtil.StringAnyMapFormat)
          .map { jsonSchema =>
            SolarResponseFormat.JsonSchema(jsonSchema)
          }

      case "regex" =>
        (json \ "regex" \ "regex").validate[String].map { regex =>
          SolarResponseFormat.Regex(regex)
        }

      case _ => JsError("Invalid SolarResponseFormat type")
    }
  }

  implicit lazy val solarResponseFormatWrites: Writes[SolarResponseFormat] = {
    case x: SolarResponseFormat.JsonSchema =>
      val jsonSchema = Json.toJson(x.jsonSchema)(JsonUtil.StringAnyMapFormat)
      Json.obj(
        "type" -> "json_schema",
        "json_schema" -> Json.obj("schema" -> jsonSchema)
      )

    case x: SolarResponseFormat.Regex =>
      Json.obj(
        "type" -> "regex",
        "regex" -> Json.obj("regex" -> x.regex)
      )
  }

  implicit lazy val solarResponseFormatFormat: Format[SolarResponseFormat] =
    Format(solarResponseFormatReads, solarResponseFormatWrites)

  implicit lazy val recencyFilterTypeFormat: Format[RecencyFilterType] =
    JsonUtil.enumFormat[RecencyFilterType](RecencyFilterType.values: _*)

  implicit lazy val sonarCreateChatCompletionSettingsFormat
    : Format[SonarCreateChatCompletionSettings] =
    Json.format[SonarCreateChatCompletionSettings]

  implicit lazy val sonarChatCompletionResponseFormat: Format[SonarChatCompletionResponse] =
    Json.format[SonarChatCompletionResponse]

  implicit lazy val sonarChatCompletionChunkResponse
    : Format[SonarChatCompletionChunkResponse] =
    Json.format[SonarChatCompletionChunkResponse]
}
