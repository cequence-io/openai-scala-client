package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{Citation, MediaBlock, TextBlock, ThinkingBlock, TextsContentBlock}
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlock, ContentBlockBase, ContentBlocks, SingleString}
import io.cequence.openaiscala.anthropic.domain.Message.{AssistantMessage, AssistantMessageContent, UserMessage, UserMessageContent}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{ContentBlockDelta, CreateMessageChunkResponse, CreateMessageResponse, DeltaText}
import io.cequence.openaiscala.anthropic.domain.settings.{ThinkingSettings, ThinkingType}
import io.cequence.openaiscala.anthropic.domain.{CacheControl, ChatRole, CitationsFlagRaw, Content, Message, SourceBlockRaw, SourceContentBlockRaw, TextContentRaw}
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

  // content block - raw - one to one with json
  implicit val textContentRawFormat: Format[TextContentRaw] = Json.format[TextContentRaw]

  implicit val citationsFlagRawFormat: Format[CitationsFlagRaw] = Json.format[CitationsFlagRaw]

  implicit val sourceBlockRawFormat: Format[SourceBlockRaw] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[SourceBlockRaw]
  }

  implicit val sourceContentBlockRawFormat: Format[SourceContentBlockRaw] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[SourceContentBlockRaw]
  }

  implicit lazy val citationFormat: Format[ContentBlock.Citation] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[ContentBlock.Citation]
  }

  private val textBlockReads: Reads[TextBlock] =
    Json.using[Json.WithDefaultValues].reads[TextBlock]

  private val textBlockWrites: Writes[TextBlock] = (
    (JsPath \ "text").write[String] and
      // TODO: revisit this - we don't write citations if empty
      (JsPath \ "citations").writeNullable[Seq[Citation]].contramap[Seq[Citation]] {
        citations =>
          if (citations.isEmpty) None else Some(citations)
      }
  )(
    // somehow unlift(TextBlock.unapply) is not working in Scala3
    (x: TextBlock) =>
      (
        x.text,
        x.citations
      )
  )

  private val textBlockFormat: Format[TextBlock] = Format(textBlockReads, textBlockWrites)

  private val thinkingBlockFormat: Format[ThinkingBlock] = Json.format[ThinkingBlock]

  implicit lazy val contentBlockWrites: Writes[ContentBlock] = {
    case x: TextBlock =>
      Json.obj("type" -> "text") ++ Json.toJson(x)(textBlockFormat).as[JsObject]

    case x: ThinkingBlock =>
      Json.obj("type" -> "thinking") ++ Json.toJson(x)(thinkingBlockFormat).as[JsObject]

    case x: MediaBlock =>
      Json
        .toJson(
          SourceContentBlockRaw(
            `type` = x.`type`,
            source = SourceBlockRaw(
              `type` = x.encoding,
              mediaType = Some(x.mediaType),
              data = Some(x.data)
            ),
            title = x.title,
            context = x.context,
            citations =
              if (x.citations.getOrElse(false)) Some(CitationsFlagRaw(true)) else None
          )
        )(sourceContentBlockRawFormat)
        .as[JsObject]

    case x: TextsContentBlock =>
      Json
        .toJson(
          SourceContentBlockRaw(
            `type` = "document",
            source = SourceBlockRaw(
              `type` = "content",
              content = Some(
                x.texts.map { text =>
                  TextContentRaw(`type` = "text", text = text)
                }
              )
            ),
            title = x.title,
            context = x.context,
            citations =
              if (x.citations.getOrElse(false)) Some(CitationsFlagRaw(true)) else None
          )
        )(sourceContentBlockRawFormat)
        .as[JsObject]
  }

  implicit lazy val contentBlockBaseWrites: Writes[ContentBlockBase] = {
    case ContentBlockBase(content, cacheControl) =>
      val jsonObject = Json.toJson(content).as[JsObject]
      jsonObject ++ cacheControlToJsObject(cacheControl)
  }

  implicit lazy val contentBlockBaseReads: Reads[ContentBlockBase] =
    (json: JsValue) =>
      for {
        mainType <- (json \ "type").validate[String]
        cacheControl <- (json \ "cache_control").validateOpt[CacheControl]
        result: ContentBlockBase <- mainType match {
          case "text" =>
            json
              .validate[TextBlock](textBlockFormat)
              .map(
                ContentBlockBase(_, cacheControl)
              )

          case "thinking" =>
            json.validate[ThinkingBlock](thinkingBlockFormat)
              .map(
                ContentBlockBase(_, cacheControl)
              )

          case imageOrDocumentType @ ("image" | "document") =>
            json.validate[SourceContentBlockRaw](sourceContentBlockRawFormat).map {
              sourceContentBlockRaw =>
                val block: ContentBlock = sourceContentBlockRaw.source match {
                  case SourceBlockRaw("content", _, _, Some(textContents)) =>
                    val texts = textContents.map(_.text)
                    TextsContentBlock(
                      texts,
                      title = sourceContentBlockRaw.title,
                      context = sourceContentBlockRaw.context,
                      citations = sourceContentBlockRaw.citations.map(_.enabled)
                    )

                  case SourceBlockRaw(encoding, Some(mediaType), Some(data), _) =>
                    MediaBlock(
                      imageOrDocumentType,
                      encoding,
                      mediaType,
                      data,
                      title = sourceContentBlockRaw.title,
                      context = sourceContentBlockRaw.context,
                      citations = sourceContentBlockRaw.citations.map(_.enabled)
                    )

                  case _ =>
                    throw new IllegalArgumentException("Unsupported or invalid source block")
                }
                ContentBlockBase(block, cacheControl)
            }

          case _ => JsError("Unsupported or invalid content block")
        }
      } yield result

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

  implicit lazy val contentBlocksFormat: Format[ContentBlocks] = Json.format[ContentBlocks]

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

  implicit lazy val thinkingTypeFormat: Format[ThinkingType] = JsonUtil.enumFormat[ThinkingType](ThinkingType.values: _*)
  implicit lazy val thinkingSettingsFormat: Format[ThinkingSettings] = Json.format[ThinkingSettings]
}
