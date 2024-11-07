package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{ImageBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.{
  ContentBlock,
  ContentBlocks,
  SingleString
}
import io.cequence.openaiscala.anthropic.domain.Message.{
  AssistantMessage,
  AssistantMessageContent,
  UserMessage,
  UserMessageContent
}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageChunkResponse,
  CreateMessageResponse,
  DeltaText
}
import io.cequence.openaiscala.anthropic.domain.{CacheControl, ChatRole, Content, Message}
import io.cequence.wsclient.JsonUtil
import play.api.libs.functional.syntax._
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json._

object JsonFormats extends JsonFormats

trait JsonFormats {

  implicit lazy val chatRoleFormat: Format[ChatRole] =
    JsonUtil.enumFormat[ChatRole](ChatRole.allValues: _*)
  implicit lazy val usageInfoFormat: Format[UsageInfo] = Json.format[UsageInfo]

  implicit lazy val userMessageFormat: Format[UserMessage] = Json.format[UserMessage]
  implicit lazy val userMessageContentFormat: Format[UserMessageContent] =
    Json.format[UserMessageContent]
  implicit lazy val assistantMessageFormat: Format[AssistantMessage] =
    Json.format[AssistantMessage]
  implicit lazy val assistantMessageContentFormat: Format[AssistantMessageContent] =
    Json.format[AssistantMessageContent]

  implicit lazy val textBlockFormat: Format[TextBlock] = Json.format[TextBlock]

  implicit lazy val contentBlocksFormat: Format[ContentBlocks] = Json.format[ContentBlocks]

  // implicit lazy val textBlockWrites: Writes[TextBlock] = Json.writes[TextBlock]
  implicit lazy val textBlockReads: Reads[TextBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.reads[TextBlock]
  }

  implicit lazy val textBlockWrites: Writes[TextBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.writes[TextBlock]
  }
  implicit lazy val imageBlockWrites: Writes[ImageBlock] =
    (block: ImageBlock) =>
      Json.obj(
        "type" -> "image",
        "source" -> Json.obj(
          "type" -> block.`type`,
          "media_type" -> block.mediaType,
          "data" -> block.data
        )
      )

  implicit lazy val contentBlockWrites: Writes[ContentBlock] = {
    case tb: TextBlock =>
      Json.obj("type" -> "text") ++ Json.toJson(tb)(textBlockWrites).as[JsObject]
    case ib: ImageBlock => Json.toJson(ib)(imageBlockWrites)
  }

  implicit lazy val contentBlockReads: Reads[ContentBlock] =
    (json: JsValue) => {
      (json \ "type").validate[String].flatMap {
        case "text" =>
          ((json \ "text").validate[String] and
            (json \ "cache_control").validateOpt[CacheControl]).tupled.flatMap {
            case (text, cacheControl) => JsSuccess(TextBlock(text, cacheControl))
            case _                    => JsError("Invalid text block")
          }

        case "image" =>
          for {
            source <- (json \ "source").validate[JsObject]
            `type` <- (source \ "type").validate[String]
            mediaType <- (source \ "media_type").validate[String]
            data <- (source \ "data").validate[String]
          } yield ImageBlock(`type`, mediaType, data)
        case _ => JsError("Unsupported or invalid content block")
      }
    }

  // CacheControl Reads and Writes
  implicit lazy val cacheControlReads: Reads[CacheControl] = Reads[CacheControl] {
    case JsString("ephemeral")  => JsSuccess(CacheControl.Ephemeral)
    case JsNull | JsUndefined() => JsSuccess(null)
    case _                      => JsError("Invalid cache control")
  }

  implicit lazy val cacheControlWrites: Writes[CacheControl] = Writes[CacheControl] {
    case CacheControl.Ephemeral => JsString("ephemeral")
  }

  implicit lazy val contentReads: Reads[Content] = new Reads[Content] {
    def reads(json: JsValue): JsResult[Content] = json match {
      case JsString(str) => JsSuccess(SingleString(str))
      case JsArray(_)    => Json.fromJson[Seq[ContentBlock]](json).map(ContentBlocks(_))
      case _             => JsError("Invalid content format")
    }
  }

  implicit lazy val baseMessageWrites: Writes[Message] = new Writes[Message] {
    def writes(message: Message): JsValue = message match {
      case UserMessage(content, cacheControl) =>
        val baseObj = Json.obj("role" -> "user", "content" -> content)
        cacheControl.fold(baseObj)(cc => baseObj + ("cache_control" -> Json.toJson(cc)))

      case UserMessageContent(content) =>
        Json.obj(
          "role" -> "user",
          "content" -> content.map(Json.toJson(_)(contentBlockWrites))
        )

      case AssistantMessage(content, cacheControl) =>
        val baseObj = Json.obj("role" -> "assistant", "content" -> content)
        cacheControl.fold(baseObj)(cc => baseObj + ("cache_control" -> Json.toJson(cc)))

      case AssistantMessageContent(content) =>
        Json.obj(
          "role" -> "assistant",
          "content" -> content.map(Json.toJson(_)(contentBlockWrites))
        )
      // Add cases for other subclasses if necessary
    }
  }

  implicit lazy val baseMessageReads: Reads[Message] = (
    (__ \ "role").read[String] and
      (__ \ "content").read[JsValue] and
      (__ \ "cache_control").readNullable[CacheControl]
  ).tupled.flatMap {
    case ("user", JsString(str), cacheControl) => Reads.pure(UserMessage(str, cacheControl))
    case ("user", json @ JsArray(_), _) => {
      Json.fromJson[Seq[ContentBlock]](json) match {
        case JsSuccess(contentBlocks, _) =>
          Reads.pure(UserMessageContent(contentBlocks))
        case JsError(errors) =>
          Reads(_ => JsError(errors))
      }
    }
    case ("assistant", JsString(str), cacheControl) =>
      Reads.pure(AssistantMessage(str, cacheControl))

    case ("assistant", json @ JsArray(_), _) => {
      Json.fromJson[Seq[ContentBlock]](json) match {
        case JsSuccess(contentBlocks, _) =>
          Reads.pure(AssistantMessageContent(contentBlocks))
        case JsError(errors) =>
          Reads(_ => JsError(errors))
      }
    }
    case _ => Reads(_ => JsError("Unsupported role or content type"))
  }

  implicit lazy val createMessageResponseReads: Reads[CreateMessageResponse] = (
    (__ \ "id").read[String] and
      (__ \ "role").read[ChatRole] and
      (__ \ "content").read[Seq[ContentBlock]].map(ContentBlocks(_)) and
      (__ \ "model").read[String] and
      (__ \ "stop_reason").readNullable[String] and
      (__ \ "stop_sequence").readNullable[String] and
      (__ \ "usage").read[UsageInfo]
  )(CreateMessageResponse.apply _)

  implicit lazy val createMessageChunkResponseReads: Reads[CreateMessageChunkResponse] =
    Json.reads[CreateMessageChunkResponse]

  implicit lazy val deltaTextReads: Reads[DeltaText] = Json.reads[DeltaText]
  implicit lazy val contentBlockDeltaReads: Reads[ContentBlockDelta] =
    Json.reads[ContentBlockDelta]
}
