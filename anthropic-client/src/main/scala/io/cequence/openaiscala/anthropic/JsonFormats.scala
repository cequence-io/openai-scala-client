package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.{BaseMessage, ChatRole, Content}
import io.cequence.openaiscala.anthropic.service.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.service.response.CreateMessageResponse.UsageInfo
import play.api.libs.functional.syntax._
import play.api.libs.json._
import Json.toJson
import io.cequence.openaiscala.JsonUtil
import io.cequence.openaiscala.anthropic.domain.Content.TextContent

object JsonFormats extends JsonFormats

trait JsonFormats {

  implicit val textContentReads: Reads[TextContent] =
    (JsPath \ "text").read[String].map(TextContent.apply)

  // implicit val imageContentReads: Reads[ImageContent] = (JsPath \ "source").read[String].map(ImageContent.apply)

  implicit val contentReads: Reads[Content] = new Reads[Content] {
    def reads(json: JsValue): JsResult[Content] = {
      (json \ "type").validate[String].flatMap {
        case "text"  => textContentReads.reads(json)
        case "image" => JsError("Image content not supported")
        case other   => JsError(s"Unknown type: $other")
      }
    }
  }

  implicit val contentWrites: Writes[Content] = {
    case textContent: Content.TextContent => Json.obj("type" -> "text", "text" -> textContent.text)
  }

  implicit val chatRoleFormat: Format[ChatRole] = JsonUtil.enumFormat[ChatRole](ChatRole.allValues:_*)

  implicit val usageInfoFormat: Format[UsageInfo] = Json.format[UsageInfo]

  implicit val createMessageResponseFormat: OFormat[CreateMessageResponse] =
    Json.format[CreateMessageResponse]

  implicit val baseMessageFormat: OFormat[BaseMessage] = Json.format[BaseMessage]

}
