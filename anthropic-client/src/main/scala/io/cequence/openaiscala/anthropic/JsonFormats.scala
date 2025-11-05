package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{
  BashCodeExecutionToolResultBlock,
  Citation,
  CodeExecutionToolResultBlock,
  ContainerUploadBlock,
  FileDocumentContentBlock,
  McpToolResultBlock,
  McpToolUseBlock,
  MediaBlock,
  RedactedThinkingBlock,
  ServerToolUseBlock,
  TextBlock,
  TextEditorCodeExecutionToolResultBlock,
  TextsContentBlock,
  ThinkingBlock,
  ToolUseBlock,
  WebSearchToolResultBlock
}
import io.cequence.openaiscala.anthropic.domain.Content.{
  ContentBlock,
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
import io.cequence.openaiscala.anthropic.domain.response.DeltaBlock.{
  DeltaSignature,
  DeltaText,
  DeltaThinking
}
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageChunkResponse,
  CreateMessageResponse,
  DeltaBlock
}
import io.cequence.openaiscala.anthropic.domain.settings.{ThinkingSettings, ThinkingType}
import io.cequence.openaiscala.anthropic.domain._
import io.cequence.openaiscala.anthropic.domain.CodeExecutionToolResultContent.CodeExecutionErrorCode
import io.cequence.openaiscala.anthropic.domain.BashCodeExecutionToolResultContent.BashCodeExecutionErrorCode
import io.cequence.openaiscala.anthropic.domain.WebSearchToolResultContent.WebSearchErrorCode
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.anthropic.domain.skills.{
  Container,
  DeleteSkillResponse,
  DeleteSkillVersionResponse,
  ListSkillVersionsResponse,
  ListSkillsResponse,
  Skill,
  SkillParams,
  SkillSource,
  SkillVersion
}
import io.cequence.openaiscala.anthropic.domain.tools.{
  BashTool,
  BashToolType,
  Citations,
  CodeExecutionTool,
  CodeExecutionToolType,
  ComputerUseTool,
  ComputerUseToolType,
  CustomTool,
  MCPServerURLDefinition,
  MCPToolConfiguration,
  MemoryTool,
  TextEditorTool,
  TextEditorToolType,
  Tool,
  ToolChoice,
  UserLocation,
  WebFetchTool,
  WebSearchTool
}
import io.cequence.openaiscala.JsonFormats.formatWithType
import io.cequence.wsclient.JsonUtil
import play.api.libs.functional.syntax._
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json._

object JsonFormats extends JsonFormats

trait JsonFormats {

  implicit lazy val chatRoleFormat: Format[ChatRole] =
    JsonUtil.enumFormat[ChatRole](ChatRole.allValues: _*)
  implicit lazy val usageInfoFormat: Format[UsageInfo] = Json.format[UsageInfo]

  implicit lazy val cacheTTLFormat: Format[CacheTTL] =
    JsonUtil.enumFormat[CacheTTL](CacheTTL.values: _*)

  // Helper to write cache control object (without wrapper)
  def writeCacheControlObject(cacheControl: CacheControl): JsObject = cacheControl match {
    case CacheControl.Ephemeral(ttl) =>
      val baseObj = Json.obj("type" -> "ephemeral")
      ttl.fold(baseObj)(t => baseObj + ("ttl" -> Json.toJson(t)))
  }

  // Helper to write cache control with wrapper (for content blocks)
  def writeJsObject(cacheControl: CacheControl): JsObject =
    Json.obj("cache_control" -> writeCacheControlObject(cacheControl))

  implicit lazy val cacheControlFormat: Format[CacheControl] = new Format[CacheControl] {
    def reads(json: JsValue): JsResult[CacheControl] = json match {
      case JsObject(map) =>
        val typeOpt = map.get("type")
        val ttlOpt = map.get("ttl").flatMap(_.asOpt[CacheTTL])

        typeOpt match {
          case Some(JsString("ephemeral")) => JsSuccess(CacheControl.Ephemeral(ttlOpt))
          case _                           => JsError(s"Invalid cache control type: $typeOpt")
        }
      case x => JsError(s"Invalid cache control ${x}")
    }

    def writes(cacheControl: CacheControl): JsValue = writeCacheControlObject(cacheControl)
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

  implicit val sourceContentBlockRawFormat: OFormat[SourceContentBlockRaw] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[SourceContentBlockRaw]
  }

  implicit lazy val citationFormat: Format[ContentBlock.Citation] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)

    // Format for DocumentCitation
    implicit val documentCitationFormat: OFormat[Citation.DocumentCitation] =
      formatWithType(Json.format[Citation.DocumentCitation])

    // Format for WebSearchResultLocation
    implicit val webSearchResultLocationFormat: OFormat[Citation.WebSearchResultLocation] =
      formatWithType(Json.format[Citation.WebSearchResultLocation])

    val reads: Reads[Citation] = Reads { json =>
      (json \ "type").asOpt[String] match {
        case Some("web_search_result_location") =>
          json.validate[Citation.WebSearchResultLocation]
        case _ =>
          json.validate[Citation.DocumentCitation]
      }
    }

    val writes: OWrites[Citation] = OWrites {
      case c: Citation.DocumentCitation =>
        Json.toJsObject(c)(documentCitationFormat)
      case c: Citation.WebSearchResultLocation =>
        Json.toJsObject(c)(webSearchResultLocationFormat)
    }

    OFormat(reads, writes)
  }

  private val textBlockReads: Reads[TextBlock] =
    Json.using[Json.WithDefaultValues].reads[TextBlock]

  private val textBlockWrites: OWrites[TextBlock] = (
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

  private val textBlockFormat: OFormat[TextBlock] = OFormat(textBlockReads, textBlockWrites)

  private val thinkingBlockFormat: OFormat[ThinkingBlock] = Json.format[ThinkingBlock]

  private val redactedThinkingBlockFormat: OFormat[RedactedThinkingBlock] =
    Json.format[RedactedThinkingBlock]

  private val toolUseBlockFormat: OFormat[ToolUseBlock] = Json.format[ToolUseBlock]

  implicit lazy val serverToolNameFormat: Format[ServerToolName] =
    JsonUtil.enumFormat[ServerToolName](ServerToolName.values: _*)

  private val serverToolUseBlockFormat: OFormat[ServerToolUseBlock] =
    Json.format[ServerToolUseBlock]

  implicit lazy val webSearchErrorCodeFormat: Format[WebSearchErrorCode] =
    JsonUtil.enumFormat[WebSearchErrorCode](WebSearchErrorCode.values: _*)

  implicit lazy val codeExecutionErrorCodeFormat: Format[CodeExecutionErrorCode] =
    JsonUtil.enumFormat[CodeExecutionErrorCode](CodeExecutionErrorCode.values: _*)

  implicit lazy val bashCodeExecutionErrorCodeFormat: Format[BashCodeExecutionErrorCode] =
    JsonUtil.enumFormat[BashCodeExecutionErrorCode](BashCodeExecutionErrorCode.values: _*)

  private implicit val webSearchToolResultBlockContentFormat
    : Format[WebSearchToolResultContent.Item] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[WebSearchToolResultContent.Item])
  }

  private implicit val webSearchToolResultErrorFormat
    : Format[WebSearchToolResultContent.Error] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[WebSearchToolResultContent.Error])
  }

  implicit lazy val webSearchToolResultContentReads: Reads[WebSearchToolResultContent] = {
    case JsArray(results) =>
      val validated = results.toList.map(_.validate[WebSearchToolResultContent.Item])
      val errors = validated.collect { case e: JsError => e }
      if (errors.nonEmpty) {
        errors.head
      } else {
        val successResults = validated.collect { case JsSuccess(value, _) => value }
        JsSuccess(WebSearchToolResultContent.Success(successResults))
      }
    case obj: JsObject =>
      obj.validate[WebSearchToolResultContent.Error]
    case _ =>
      JsError("Expected array of results or error object")
  }

  implicit lazy val webSearchToolResultContentWrites: Writes[WebSearchToolResultContent] = {
    case WebSearchToolResultContent.Success(results) =>
      Json.toJson(results)(Writes.seq(webSearchToolResultBlockContentFormat))
    case error: WebSearchToolResultContent.Error =>
      Json.toJson(error)(webSearchToolResultErrorFormat)
  }

  implicit lazy val webSearchToolResultContentFormat: Format[WebSearchToolResultContent] =
    Format(webSearchToolResultContentReads, webSearchToolResultContentWrites)

  private val webSearchToolResultBlockFormat: OFormat[WebSearchToolResultBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[WebSearchToolResultBlock]
  }

  private implicit val toolResultContentFormat: OFormat[MCPToolResultItem] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.using[Json.WithDefaultValues].format[MCPToolResultItem])
  }

  private val mcpToolUseBlockFormat: OFormat[McpToolUseBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[McpToolUseBlock])
  }

  implicit lazy val mcpToolResultContentReads: Reads[McpToolResultContent] = {
    case JsString(str) => JsSuccess(McpToolResultString(str))
    case JsArray(items) =>
      val validated = items.toList.map(_.validate[MCPToolResultItem])
      val errors = validated.collect { case e: JsError => e }
      if (errors.nonEmpty) {
        errors.head
      } else {
        val successResults = validated.collect { case JsSuccess(value, _) => value }
        JsSuccess(McpToolResultStructured(successResults))
      }
    case _ => JsError("Expected string or array of ToolResultContent")
  }

  implicit lazy val mcpToolResultContentWrites: Writes[McpToolResultContent] = {
    case McpToolResultString(value) => JsString(value)
    case McpToolResultStructured(results) =>
      Json.toJson(results)(Writes.seq(toolResultContentFormat))
  }

  implicit lazy val mcpToolResultContentFormat: Format[McpToolResultContent] =
    Format(mcpToolResultContentReads, mcpToolResultContentWrites)

  private val mcpToolResultBlockFormat: OFormat[McpToolResultBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[McpToolResultBlock])
  }

  private val containerUploadBlockFormat: OFormat[ContainerUploadBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[ContainerUploadBlock])
  }

  private implicit val responseCodeExecutionOutputBlockFormat
    : Format[CodeExecutionToolResultContent.Item] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[CodeExecutionToolResultContent.Item])
  }

  private implicit val codeExecutionToolErrorFormat
    : OFormat[CodeExecutionToolResultContent.Error] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[CodeExecutionToolResultContent.Error]
  }

  private implicit val codeExecutionToolResultFormat
    : OFormat[CodeExecutionToolResultContent.Success] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[CodeExecutionToolResultContent.Success]
  }

  implicit lazy val codeExecutionToolResultContentReads
    : Reads[CodeExecutionToolResultContent] = (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "code_execution_tool_result_error" =>
        json.validate[CodeExecutionToolResultContent.Error](codeExecutionToolErrorFormat)

      case "code_execution_result" =>
        json.validate[CodeExecutionToolResultContent.Success](codeExecutionToolResultFormat)
      case other =>
        JsError(s"Unknown code execution tool result content type: $other")
    }

  implicit lazy val codeExecutionToolResultContentWrites
    : OWrites[CodeExecutionToolResultContent] = {
    case error: CodeExecutionToolResultContent.Error =>
      Json.toJsObject(error)(codeExecutionToolErrorFormat)

    case result: CodeExecutionToolResultContent.Success =>
      Json.toJsObject(result)(codeExecutionToolResultFormat)
  }

  implicit lazy val codeExecutionToolResultContentFormat
    : OFormat[CodeExecutionToolResultContent] =
    formatWithType(
      OFormat(
        codeExecutionToolResultContentReads,
        codeExecutionToolResultContentWrites
      )
    )

  private val codeExecutionToolResultBlockFormat: OFormat[CodeExecutionToolResultBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(
      Json.format[CodeExecutionToolResultBlock]
    )
  }

  private implicit val responseBashCodeExecutionOutputBlockFormat
    : Format[BashCodeExecutionToolResultContent.Item] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[BashCodeExecutionToolResultContent.Item])
  }

  private implicit val bashCodeExecutionToolErrorFormat
    : OFormat[BashCodeExecutionToolResultContent.Error] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[BashCodeExecutionToolResultContent.Error]
  }

  private implicit val bashCodeExecutionToolResultFormat
    : OFormat[BashCodeExecutionToolResultContent.Success] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[BashCodeExecutionToolResultContent.Success]
  }

  implicit lazy val bashCodeExecutionToolResultContentReads
    : Reads[BashCodeExecutionToolResultContent] = (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "bash_code_execution_tool_result_error" =>
        json
          .validate[BashCodeExecutionToolResultContent.Error](bashCodeExecutionToolErrorFormat)
      case "bash_code_execution_result" =>
        json.validate[BashCodeExecutionToolResultContent.Success](
          bashCodeExecutionToolResultFormat
        )
      case other =>
        JsError(s"Unknown bash code execution tool result content type: $other")
    }

  implicit lazy val bashCodeExecutionToolResultContentWrites
    : OWrites[BashCodeExecutionToolResultContent] = {
    case error: BashCodeExecutionToolResultContent.Error =>
      Json.toJsObject(error)(bashCodeExecutionToolErrorFormat)
    case result: BashCodeExecutionToolResultContent.Success =>
      Json.toJsObject(result)(bashCodeExecutionToolResultFormat)
  }

  implicit lazy val bashCodeExecutionToolResultContentFormat
    : OFormat[BashCodeExecutionToolResultContent] =
    formatWithType(
      OFormat(
        bashCodeExecutionToolResultContentReads,
        bashCodeExecutionToolResultContentWrites
      )
    )

  private val bashCodeExecutionToolResultBlockFormat
    : OFormat[BashCodeExecutionToolResultBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[BashCodeExecutionToolResultBlock]
  }

  // TEXT EDITOR CODE EXECUTION

  implicit lazy val textEditorCodeExecutionErrorCodeFormat
    : Format[TextEditorCodeExecutionToolResultContent.TextEditorCodeExecutionErrorCode] =
    JsonUtil
      .enumFormat[TextEditorCodeExecutionToolResultContent.TextEditorCodeExecutionErrorCode](
        TextEditorCodeExecutionToolResultContent.TextEditorCodeExecutionErrorCode.values: _*
      )

  implicit lazy val fileTypeFormat: Format[TextEditorCodeExecutionToolResultContent.FileType] =
    JsonUtil.enumFormat[TextEditorCodeExecutionToolResultContent.FileType](
      TextEditorCodeExecutionToolResultContent.FileType.values: _*
    )

  private implicit val textEditorCodeExecutionToolErrorFormat
    : OFormat[TextEditorCodeExecutionToolResultContent.Error] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[TextEditorCodeExecutionToolResultContent.Error]
  }

  private implicit val textEditorCodeExecutionViewResultFormat
    : OFormat[TextEditorCodeExecutionToolResultContent.ViewResult] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[TextEditorCodeExecutionToolResultContent.ViewResult]
  }

  private implicit val textEditorCodeExecutionCreateResultFormat
    : OFormat[TextEditorCodeExecutionToolResultContent.CreateResult] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[TextEditorCodeExecutionToolResultContent.CreateResult]
  }

  private implicit val textEditorCodeExecutionReplaceResultFormat
    : OFormat[TextEditorCodeExecutionToolResultContent.ReplaceResult] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json
      .using[Json.WithDefaultValues]
      .format[TextEditorCodeExecutionToolResultContent.ReplaceResult]
  }

  implicit lazy val textEditorCodeExecutionToolResultContentReads
    : Reads[TextEditorCodeExecutionToolResultContent] = (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "text_editor_code_execution_tool_result_error" =>
        json.validate[TextEditorCodeExecutionToolResultContent.Error](
          textEditorCodeExecutionToolErrorFormat
        )
      case "text_editor_code_execution_view_result" =>
        json.validate[TextEditorCodeExecutionToolResultContent.ViewResult](
          textEditorCodeExecutionViewResultFormat
        )
      case "text_editor_code_execution_create_result" =>
        json.validate[TextEditorCodeExecutionToolResultContent.CreateResult](
          textEditorCodeExecutionCreateResultFormat
        )
      case "text_editor_code_execution_str_replace_result" =>
        json.validate[TextEditorCodeExecutionToolResultContent.ReplaceResult](
          textEditorCodeExecutionReplaceResultFormat
        )
      case other =>
        JsError(s"Unknown text editor code execution tool result content type: $other")
    }

  implicit lazy val textEditorCodeExecutionToolResultContentWrites
    : OWrites[TextEditorCodeExecutionToolResultContent] = OWrites {
    case x: TextEditorCodeExecutionToolResultContent.Error =>
      Json.toJsObject(x)(textEditorCodeExecutionToolErrorFormat)
    case x: TextEditorCodeExecutionToolResultContent.ViewResult =>
      Json.toJsObject(x)(textEditorCodeExecutionViewResultFormat)
    case x: TextEditorCodeExecutionToolResultContent.CreateResult =>
      Json.toJsObject(x)(textEditorCodeExecutionCreateResultFormat)
    case x: TextEditorCodeExecutionToolResultContent.ReplaceResult =>
      Json.toJsObject(x)(textEditorCodeExecutionReplaceResultFormat)
  }

  implicit lazy val textEditorCodeExecutionToolResultContentFormat
    : OFormat[TextEditorCodeExecutionToolResultContent] = {
    formatWithType(
      OFormat(
        textEditorCodeExecutionToolResultContentReads,
        textEditorCodeExecutionToolResultContentWrites
      )
    )
  }

  private val textEditorCodeExecutionToolResultBlockFormat
    : OFormat[TextEditorCodeExecutionToolResultBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(
      Json.format[TextEditorCodeExecutionToolResultBlock]
    )
  }

  private val contentBlockReads: Reads[ContentBlock] = (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "text" =>
        json.validate[TextBlock](textBlockFormat)

      case "thinking" =>
        json.validate[ThinkingBlock](thinkingBlockFormat)

      case "redacted_thinking" =>
        json.validate[RedactedThinkingBlock](redactedThinkingBlockFormat)

      case "tool_use" =>
        json.validate[ToolUseBlock](toolUseBlockFormat)

      case "server_tool_use" =>
        json.validate[ServerToolUseBlock](serverToolUseBlockFormat)

      case "web_search_tool_result" =>
        json.validate[WebSearchToolResultBlock](webSearchToolResultBlockFormat)

      case "mcp_tool_use" =>
        json.validate[McpToolUseBlock](mcpToolUseBlockFormat)

      case "mcp_tool_result" =>
        json.validate[McpToolResultBlock](mcpToolResultBlockFormat)

      case "container_upload" =>
        json.validate[ContainerUploadBlock](containerUploadBlockFormat)

      case "code_execution_tool_result" =>
        json.validate[CodeExecutionToolResultBlock](codeExecutionToolResultBlockFormat)

      case "bash_code_execution_tool_result" =>
        json.validate[BashCodeExecutionToolResultBlock](bashCodeExecutionToolResultBlockFormat)

      case "text_editor_code_execution_tool_result" =>
        json.validate[TextEditorCodeExecutionToolResultBlock](
          textEditorCodeExecutionToolResultBlockFormat
        )

      case imageOrDocumentType @ ("image" | "document") =>
        json.validate[SourceContentBlockRaw](sourceContentBlockRawFormat).map {
          sourceContentBlockRaw =>
            sourceContentBlockRaw.source match {
              case SourceBlockRaw("content", _, _, Some(textContents), _) =>
                val texts = textContents.map(_.text)
                TextsContentBlock(
                  texts,
                  title = sourceContentBlockRaw.title,
                  context = sourceContentBlockRaw.context,
                  citations = sourceContentBlockRaw.citations.map(_.enabled)
                )

              case SourceBlockRaw("file", _, _, _, Some(fileId)) =>
                FileDocumentContentBlock(
                  fileId = fileId,
                  title = sourceContentBlockRaw.title,
                  context = sourceContentBlockRaw.context,
                  citations = sourceContentBlockRaw.citations.map(_.enabled)
                )

              case SourceBlockRaw(encoding, Some(mediaType), Some(data), _, _) =>
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
        }

      case _ =>
        JsError("Unsupported or invalid content block type")
    }

  private val contentBlockWrites: OWrites[ContentBlock] = {
    case x: TextBlock =>
      Json.toJsObject(x)(textBlockFormat)

    case x: ThinkingBlock =>
      Json.toJsObject(x)(thinkingBlockFormat)

    case x: RedactedThinkingBlock =>
      Json.toJsObject(x)(redactedThinkingBlockFormat)

    case x: ToolUseBlock =>
      Json.toJsObject(x)(toolUseBlockFormat)

    case x: ServerToolUseBlock =>
      Json.toJsObject(x)(serverToolUseBlockFormat)

    case x: WebSearchToolResultBlock =>
      Json.toJsObject(x)(webSearchToolResultBlockFormat)

    case x: McpToolUseBlock =>
      Json.toJsObject(x)(mcpToolUseBlockFormat)

    case x: McpToolResultBlock =>
      Json.toJsObject(x)(mcpToolResultBlockFormat)

    case x: ContainerUploadBlock =>
      Json.toJsObject(x)(containerUploadBlockFormat)

    case x: CodeExecutionToolResultBlock =>
      Json.toJsObject(x)(codeExecutionToolResultBlockFormat)

    case x: BashCodeExecutionToolResultBlock =>
      Json.toJsObject(x)(bashCodeExecutionToolResultBlockFormat)

    case x: TextEditorCodeExecutionToolResultBlock =>
      Json.toJsObject(x)(textEditorCodeExecutionToolResultBlockFormat)

    case x: MediaBlock =>
      Json.toJsObject(
        SourceContentBlockRaw(
          source = SourceBlockRaw(
            `type` = x.encoding,
            mediaType = Some(x.mediaType),
            data = Some(x.data)
          ),
          title = x.title,
          context = x.context,
          citations = if (x.citations.getOrElse(false)) Some(CitationsFlagRaw(true)) else None
        )
      )(sourceContentBlockRawFormat)

    case x: TextsContentBlock =>
      Json.toJsObject(
        SourceContentBlockRaw(
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
          citations = if (x.citations.getOrElse(false)) Some(CitationsFlagRaw(true)) else None
        )
      )(sourceContentBlockRawFormat)

    case x: FileDocumentContentBlock =>
      Json.toJsObject(
        SourceContentBlockRaw(
          source = SourceBlockRaw(
            `type` = "file",
            fileId = Some(x.fileId)
          ),
          title = x.title,
          context = x.context,
          citations = if (x.citations.getOrElse(false)) Some(CitationsFlagRaw(true)) else None
        )
      )(sourceContentBlockRawFormat)
  }

  implicit val contentBlockFormat: OFormat[ContentBlock] =
    formatWithType(OFormat(contentBlockReads, contentBlockWrites))

  implicit lazy val contentBlockBaseWrites: OWrites[ContentBlockBase] = {
    case ContentBlockBase(content, cacheControl) =>
      val jsonObject = Json.toJsObject(content)(contentBlockFormat)
      jsonObject ++ cacheControlToJsObject(cacheControl)
  }

  implicit lazy val contentBlockBaseReads: Reads[ContentBlockBase] =
    (json: JsValue) =>
      for {
        cacheControl <- (json \ "cache_control").validateOpt[CacheControl]
        content <- contentBlockFormat.reads(json)
      } yield ContentBlockBase(content, cacheControl)

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

  implicit lazy val contentReads: Reads[Content] = {
    case JsString(str)     => JsSuccess(SingleString(str))
    case json @ JsArray(_) => Json.fromJson[Seq[ContentBlockBase]](json).map(ContentBlocks(_))
    case _                 => JsError("Invalid content format")
  }

  implicit lazy val contentWrites: Writes[Content] = new Writes[Content] {
    def writes(content: Content): JsValue = content match {
      case SingleString(text, cacheControl) =>
        Json.obj("content" -> text) ++ cacheControlToJsObject(cacheControl)
      case ContentBlocks(blocks) =>
        Json.obj("content" -> Json.toJson(blocks)(Writes.seq(contentBlockBaseWrites)))
    }
  }

  implicit lazy val baseMessageWrites: Writes[Message] = {
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

  private val deltaTextFormat: OFormat[DeltaText] = Json.format[DeltaText]
  private val deltaThinkingFormat: OFormat[DeltaThinking] = Json.format[DeltaThinking]
  private val deltaSignatureFormat: OFormat[DeltaSignature] = Json.format[DeltaSignature]

  private val deltaBlockReads: Reads[DeltaBlock] = (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "text_delta" =>
        json.validate[DeltaText](deltaTextFormat)

      case "thinking_delta" =>
        json.validate[DeltaThinking](deltaThinkingFormat)

      case "signature_delta" =>
        json.validate[DeltaSignature](deltaSignatureFormat)

      case _ =>
        JsError("Unsupported or invalid delta block type")
    }

  private val deltaBlockWrites: OWrites[DeltaBlock] = {
    case deltaText: DeltaText =>
      Json.toJsObject(deltaText)(deltaTextFormat)

    case deltaThinking: DeltaThinking =>
      Json.toJsObject(deltaThinking)(deltaThinkingFormat)

    case deltaSignature: DeltaSignature =>
      Json.toJsObject(deltaSignature)(deltaSignatureFormat)
  }

  implicit lazy val deltaBlockFormat: Format[DeltaBlock] =
    formatWithType(OFormat(deltaBlockReads, deltaBlockWrites))

  implicit lazy val contentBlockDeltaReads: Reads[ContentBlockDelta] =
    Json.reads[ContentBlockDelta]

  implicit lazy val thinkingTypeFormat: Format[ThinkingType] =
    JsonUtil.enumFormat[ThinkingType](ThinkingType.values: _*)
  implicit lazy val thinkingSettingsFormat: Format[ThinkingSettings] =
    Json.format[ThinkingSettings]

  // Skills API formats
  implicit lazy val skillSourceFormat: Format[SkillSource] =
    JsonUtil.enumFormat[SkillSource](SkillSource.values: _*)

  implicit lazy val skillFormat: Format[Skill] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[Skill])
  }

  implicit lazy val listSkillsResponseFormat: Format[ListSkillsResponse] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[ListSkillsResponse]
  }

  implicit lazy val deleteSkillResponseFormat: Format[DeleteSkillResponse] =
    formatWithType(Json.format[DeleteSkillResponse])

  implicit lazy val skillVersionFormat: Format[SkillVersion] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[SkillVersion])
  }

  implicit lazy val listSkillVersionsResponseFormat: Format[ListSkillVersionsResponse] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[ListSkillVersionsResponse]
  }

  implicit lazy val deleteSkillVersionResponseFormat: Format[DeleteSkillVersionResponse] =
    formatWithType(Json.format[DeleteSkillVersionResponse])

  // FILES

  implicit lazy val fileMetadataFormat: Format[FileMetadata] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[FileMetadata])
  }

  implicit lazy val fileListResponseFormat: Format[FileListResponse] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[FileListResponse]
  }

  implicit lazy val fileDeleteResponseFormat: Format[FileDeleteResponse] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[FileDeleteResponse])
  }

  implicit lazy val skillParamsFormat: Format[SkillParams] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[SkillParams]
  }

  implicit lazy val containerFormat: Format[Container] =
    Json.using[Json.WithDefaultValues].format[Container]

  // Tool formats
  implicit lazy val bashToolTypeFormat: Format[BashToolType] =
    JsonUtil.enumFormat[BashToolType](BashToolType.values: _*)

  implicit lazy val codeExecutionToolTypeFormat: Format[CodeExecutionToolType] =
    JsonUtil.enumFormat[CodeExecutionToolType](CodeExecutionToolType.values: _*)

  implicit lazy val computerUseToolTypeFormat: Format[ComputerUseToolType] =
    JsonUtil.enumFormat[ComputerUseToolType](ComputerUseToolType.values: _*)

  implicit lazy val textEditorToolTypeFormat: Format[TextEditorToolType] =
    JsonUtil.enumFormat[TextEditorToolType](TextEditorToolType.values: _*)

  implicit lazy val userLocationFormat: Format[UserLocation] = Json.format[UserLocation]

  implicit lazy val citationsFormat: Format[Citations] = Json.format[Citations]

  private val customToolFormat: OFormat[CustomTool] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[CustomTool]
  }

  private val bashToolFormat: OFormat[BashTool] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[BashTool]
  }

  private val codeExecutionToolFormat: OFormat[CodeExecutionTool] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[CodeExecutionTool]
  }

  private val computerUseToolFormat: OFormat[ComputerUseTool] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[ComputerUseTool]
  }

  private val memoryToolFormat: OFormat[MemoryTool] = OFormat(
    Reads[MemoryTool](_ => JsSuccess(MemoryTool())),
    OWrites[MemoryTool](_ => Json.obj())
  )

  private val textEditorToolFormat: OFormat[TextEditorTool] = OFormat(
    Reads[TextEditorTool] { json =>
      for {
        toolType <- (json \ "type").validate[TextEditorToolType]
        cacheControl <- (json \ "cache_control").validateOpt[CacheControl]
      } yield TextEditorTool(toolType, cacheControl)
    },
    OWrites[TextEditorTool] { tool =>
      val baseObj = Json.obj("type" -> tool.`type`.toString)
      tool.cacheControl.fold(baseObj)(cc => baseObj + ("cache_control" -> Json.toJson(cc)))
    }
  )

  private val webSearchToolFormat: OFormat[WebSearchTool] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)

    val reads = Json.reads[WebSearchTool]
    val writes = OWrites[WebSearchTool] { tool =>
      var obj = Json.obj()

      // Only include allowedDomains if non-empty
      if (tool.allowedDomains.nonEmpty) {
        obj = obj + ("allowed_domains" -> Json.toJson(tool.allowedDomains))
      }

      // Only include blockedDomains if non-empty
      if (tool.blockedDomains.nonEmpty) {
        obj = obj + ("blocked_domains" -> Json.toJson(tool.blockedDomains))
      }

      // Only include maxUses if defined
      tool.maxUses.foreach(v => obj = obj + ("max_uses" -> JsNumber(v)))

      // Only include userLocation if defined
      tool.userLocation.foreach(v => obj = obj + ("user_location" -> Json.toJson(v)))

      obj
    }

    OFormat(reads, writes)
  }

  private val webFetchToolFormat: OFormat[WebFetchTool] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)

    val reads = Json.reads[WebFetchTool]
    val writes = OWrites[WebFetchTool] { tool =>
      var obj = Json.obj()

      // Only include allowedDomains if non-empty
      if (tool.allowedDomains.nonEmpty) {
        obj = obj + ("allowed_domains" -> Json.toJson(tool.allowedDomains))
      }

      // Only include blockedDomains if non-empty
      if (tool.blockedDomains.nonEmpty) {
        obj = obj + ("blocked_domains" -> Json.toJson(tool.blockedDomains))
      }

      // Only include citations if defined
      tool.citations.foreach(v => obj = obj + ("citations" -> Json.toJson(v)))

      // Only include maxContentTokens if defined
      tool.maxContentTokens.foreach(v => obj = obj + ("max_content_tokens" -> JsNumber(v)))

      // Only include maxUses if defined
      tool.maxUses.foreach(v => obj = obj + ("max_uses" -> JsNumber(v)))

      obj
    }

    OFormat(reads, writes)
  }

  implicit lazy val mcpToolConfigurationFormat: Format[MCPToolConfiguration] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.using[Json.WithDefaultValues].format[MCPToolConfiguration]
  }

  private val mcpServerURLDefinitionFormat: OFormat[MCPServerURLDefinition] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[MCPServerURLDefinition])
  }

  implicit val mcpServerURLDefinitionWrites: OWrites[MCPServerURLDefinition] =
    mcpServerURLDefinitionFormat

  implicit lazy val toolWrites: OWrites[Tool] = (tool: Tool) => {
    val jsonObject: JsObject = tool match {
      case t: CustomTool        => Json.toJsObject(t)(customToolFormat)
      case t: BashTool          => Json.toJsObject(t)(bashToolFormat)
      case t: CodeExecutionTool => Json.toJsObject(t)(codeExecutionToolFormat)
      case t: ComputerUseTool   => Json.toJsObject(t)(computerUseToolFormat)
      case t: MemoryTool        => Json.toJsObject(t)(memoryToolFormat)
      case t: TextEditorTool    => Json.toJsObject(t)(textEditorToolFormat)
      case t: WebSearchTool     => Json.toJsObject(t)(webSearchToolFormat)
      case t: WebFetchTool      => Json.toJsObject(t)(webFetchToolFormat)
    }

    // Centrally add name and type fields for all tools
    jsonObject ++ Json.obj(
      "name" -> tool.name,
      "type" -> tool.`type`.toString
    )
  }

  implicit lazy val toolReads: Reads[Tool] = (json: JsValue) => {
    val toolType = (json \ "type").asOpt[String]
    val toolName = (json \ "name").asOpt[String]

    (toolType, toolName) match {
      case (Some("custom"), _) | (None, _) =>
        json.validate[CustomTool](customToolFormat)

      case (Some(t), Some("bash")) if t.startsWith("bash_") =>
        json.validate[BashTool](bashToolFormat)

      case (Some(t), Some("code_execution")) if t.startsWith("code_execution_") =>
        json.validate[CodeExecutionTool](codeExecutionToolFormat)

      case (Some(t), Some("computer")) if t.startsWith("computer_") =>
        json.validate[ComputerUseTool](computerUseToolFormat)

      case (Some("memory_20250818"), Some("memory")) =>
        json.validate[MemoryTool](memoryToolFormat)

      case (Some(t), Some("str_replace_editor" | "str_replace_based_edit_tool"))
          if t.startsWith("text_editor_") =>
        json.validate[TextEditorTool](textEditorToolFormat)

      case (Some("web_search_20250305"), Some("web_search")) =>
        json.validate[WebSearchTool](webSearchToolFormat)

      case (Some("web_fetch_20250910"), Some("web_fetch")) =>
        json.validate[WebFetchTool](webFetchToolFormat)

      case _ =>
        JsError(s"Unknown tool type: $toolType with name: $toolName")
    }
  }

  implicit lazy val toolFormat: OFormat[Tool] = OFormat(toolReads, toolWrites)

  // Tool Choice formats
  implicit lazy val toolChoiceFormat: OFormat[ToolChoice] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)

    val autoFormat: OFormat[ToolChoice.Auto] = Json.format[ToolChoice.Auto]
    val anyFormat: OFormat[ToolChoice.Any] = Json.format[ToolChoice.Any]
    val toolFormat: OFormat[ToolChoice.Tool] = Json.format[ToolChoice.Tool]

    val reads: Reads[ToolChoice] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "auto" => autoFormat.reads(json)
        case "any"  => anyFormat.reads(json)
        case "tool" => toolFormat.reads(json)
        case "none" => JsSuccess(ToolChoice.None)
        case other  => JsError(s"Unknown tool choice type: $other")
      }
    }

    val writes: OWrites[ToolChoice] = OWrites {
      case tc: ToolChoice.Auto => autoFormat.writes(tc)
      case tc: ToolChoice.Any  => anyFormat.writes(tc)
      case tc: ToolChoice.Tool => toolFormat.writes(tc)
      case ToolChoice.None     => Json.obj()
    }

    formatWithType(OFormat(reads, writes))
  }
}
