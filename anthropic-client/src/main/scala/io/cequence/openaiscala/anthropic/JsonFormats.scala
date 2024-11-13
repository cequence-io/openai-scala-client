package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{MediaBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.{
  ContentBlockBase,
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

  def writeJsObject(cacheControl: CacheControl): JsObject = cacheControl match {
    case CacheControl.Ephemeral =>
      Json.obj("cache_control" -> Json.obj("type" -> "ephemeral"))
  }

  implicit lazy val cacheControlFormat: Format[CacheControl] = new Format[CacheControl] {
    def reads(json: JsValue): JsResult[CacheControl] = json match {
      case JsObject(map) =>
        if (map == Map("type" -> JsString("ephemeral"))) JsSuccess(CacheControl.Ephemeral)
        else JsError(s"Invalid cache control $map")
      case x => {
        JsError(s"Invalid cache control ${x}")
      }
    }

    def writes(cacheControl: CacheControl): JsValue = writeJsObject(cacheControl)
  }

  implicit lazy val cacheControlOptionFormat: Format[Option[CacheControl]] =
    new Format[Option[CacheControl]] {
      def reads(json: JsValue): JsResult[Option[CacheControl]] = json match {
        case JsNull => JsSuccess(None)
        case _      => cacheControlFormat.reads(json).map(Some(_))
      }

      def writes(option: Option[CacheControl]): JsValue = option match {
        case None               => JsNull
        case Some(cacheControl) => cacheControlFormat.writes(cacheControl)
      }
    }

  implicit lazy val contentBlockBaseWrites: Writes[ContentBlockBase] = {
    case ContentBlockBase(textBlock @ TextBlock(_), cacheControl) =>
      Json.obj("type" -> "text") ++
        Json.toJson(textBlock)(textBlockWrites).as[JsObject] ++
        cacheControlToJsObject(cacheControl)
    case ContentBlockBase(media @ MediaBlock(_, _, _, _), maybeCacheControl) =>
      Json.toJson(media)(mediaBlockWrites).as[JsObject] ++
        cacheControlToJsObject(maybeCacheControl)

  }

  implicit lazy val contentBlockBaseReads: Reads[ContentBlockBase] =
    (json: JsValue) => {
      (json \ "type").validate[String].flatMap {
        case "text" =>
          ((json \ "text").validate[String] and
            (json \ "cache_control").validateOpt[CacheControl]).tupled.flatMap {
            case (text, cacheControl) =>
              JsSuccess(ContentBlockBase(TextBlock(text), cacheControl))
            case _ => JsError("Invalid text block")
          }

        case imageOrDocument @ ("image" | "document") =>
          for {
            source <- (json \ "source").validate[JsObject]
            `type` <- (source \ "type").validate[String]
            mediaType <- (source \ "media_type").validate[String]
            data <- (source \ "data").validate[String]
            cacheControl <- (json \ "cache_control").validateOpt[CacheControl]
          } yield ContentBlockBase(
            MediaBlock(imageOrDocument, `type`, mediaType, data),
            cacheControl
          )

        case _ => JsError("Unsupported or invalid content block")
      }
    }

  implicit lazy val contentBlockBaseFormat: Format[ContentBlockBase] = Format(
    contentBlockBaseReads,
    contentBlockBaseWrites
  )
  implicit lazy val contentBlockBaseSeqFormat: Format[Seq[ContentBlockBase]] = Format(
    Reads.seq(contentBlockBaseReads),
    Writes.seq(contentBlockBaseWrites)
  )

  implicit lazy val userMessageFormat: Format[UserMessage] = Json.format[UserMessage]
  implicit lazy val userMessageContentFormat: Format[UserMessageContent] =
    Json.format[UserMessageContent]
  implicit lazy val assistantMessageFormat: Format[AssistantMessage] =
    Json.format[AssistantMessage]
  implicit lazy val assistantMessageContentFormat: Format[AssistantMessageContent] =
    Json.format[AssistantMessageContent]

  implicit lazy val textBlockFormat: Format[TextBlock] = Json.format[TextBlock]

  implicit lazy val contentBlocksFormat: Format[ContentBlocks] = Json.format[ContentBlocks]

  implicit lazy val textBlockReads: Reads[TextBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.reads[TextBlock]
  }

  implicit lazy val textBlockWrites: Writes[TextBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.writes[TextBlock]
  }

  implicit lazy val mediaBlockWrites: Writes[MediaBlock] =
    (block: MediaBlock) =>
      Json.obj(
        "type" -> block.`type`,
        "source" -> Json.obj(
          "type" -> block.encoding,
          "media_type" -> block.mediaType,
          "data" -> block.data
        )
      )

  private def cacheControlToJsObject(maybeCacheControl: Option[CacheControl]): JsObject =
    maybeCacheControl.fold(Json.obj())(cc => writeJsObject(cc))

  implicit lazy val contentReads: Reads[Content] = new Reads[Content] {
    def reads(json: JsValue): JsResult[Content] = json match {
      case JsString(str) => JsSuccess(SingleString(str))
      case JsArray(_)    => Json.fromJson[Seq[ContentBlockBase]](json).map(ContentBlocks(_))
      case _             => JsError("Invalid content format")
    }
  }

  implicit lazy val contentWrites: Writes[Content] = new Writes[Content] {
    def writes(content: Content): JsValue = content match {
      case SingleString(text, cacheControl) =>
        Json.obj("content" -> text) ++ cacheControlToJsObject(cacheControl)
      case ContentBlocks(blocks) =>
        Json.obj("content" -> Json.toJson(blocks)(Writes.seq(contentBlockBaseWrites)))
    }
  }

  implicit lazy val baseMessageWrites: Writes[Message] = new Writes[Message] {
    def writes(message: Message): JsValue = message match {
      case UserMessage(content, cacheControl) =>
        val baseObj = Json.obj("role" -> "user", "content" -> content)
        baseObj ++ cacheControlToJsObject(cacheControl)

      case UserMessageContent(content) =>
        Json.obj(
          "role" -> "user",
          "content" -> content.map(Json.toJson(_)(contentBlockBaseWrites))
        )

      case AssistantMessage(content, cacheControl) =>
        val baseObj = Json.obj("role" -> "assistant", "content" -> content)
        baseObj ++ cacheControlToJsObject(cacheControl)

      case AssistantMessageContent(content) =>
        Json.obj(
          "role" -> "assistant",
          "content" -> content.map(Json.toJson(_)(contentBlockBaseWrites))
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
      Json.fromJson[Seq[ContentBlockBase]](json) match {
        case JsSuccess(contentBlocks, _) =>
          Reads.pure(UserMessageContent(contentBlocks))
        case JsError(errors) =>
          Reads(_ => JsError(errors))
      }
    }
    case ("assistant", JsString(str), cacheControl) =>
      Reads.pure(AssistantMessage(str, cacheControl))

    case ("assistant", json @ JsArray(_), _) => {
      Json.fromJson[Seq[ContentBlockBase]](json) match {
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
      (__ \ "content").read[Seq[ContentBlockBase]].map(ContentBlocks(_)) and
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
