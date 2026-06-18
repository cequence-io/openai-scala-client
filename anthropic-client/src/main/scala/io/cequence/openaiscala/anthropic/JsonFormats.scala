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
  WebFetchToolResultBlock,
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
import io.cequence.openaiscala.anthropic.domain.settings.{
  OutputConfig,
  OutputEffort,
  Speed,
  ThinkingSettings,
  ThinkingType
}
import io.cequence.openaiscala.anthropic.domain._
import io.cequence.openaiscala.anthropic.domain.OutputFormat
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
  MCPToolConfig,
  MCPToolConfiguration,
  MCPToolset,
  MemoryTool,
  TextEditorTool,
  TextEditorToolType,
  Tool,
  ToolChoice,
  UserLocation,
  WebFetchTool,
  WebSearchTool
}
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Agent,
  AgentModelConfig,
  AgentTool,
  AgentToolConfig,
  Environment,
  EnvironmentConfig,
  EnvironmentDeleteResponse,
  EnvironmentScope,
  Multiagent,
  MultiagentMember,
  Networking,
  Packages,
  PagedResponse,
  PermissionPolicy,
  SelfHostedWork,
  Session,
  SessionDeleteResponse,
  SessionEvent,
  SessionEventEnvelope,
  SessionResource,
  SessionStatus,
  SessionThread,
  SessionThreadStatus,
  SessionWorkData,
  Checkout,
  MemoryStoreAccess,
  OutcomeRubric,
  AgentReference,
  Deployment,
  DeploymentInitialEvent,
  DeploymentPausedReason,
  DeploymentRun,
  DeploymentStatus,
  Schedule,
  Vault,
  Credential,
  CredentialAuth,
  CredentialNetworking,
  McpOAuthRefresh,
  TokenEndpointAuth,
  WorkHeartbeatResponse,
  WorkQueueStats,
  WorkState
}
import io.cequence.openaiscala.JsonFormats.formatWithType
import io.cequence.openaiscala.domain.JsonSchema
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

  implicit val citationsFlagRawFormat: Format[CitationsFlag] = Json.format[CitationsFlag]

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

  implicit lazy val webFetchErrorCodeFormat
    : Format[WebFetchToolResultContent.WebFetchErrorCode] =
    JsonUtil.enumFormat[WebFetchToolResultContent.WebFetchErrorCode](
      WebFetchToolResultContent.WebFetchErrorCode.values: _*
    )

  private implicit val webFetchSourceFormat: OFormat[WebFetchToolResultContent.Source] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[WebFetchToolResultContent.Source]
  }

  private implicit val webFetchDocumentFormat: OFormat[WebFetchToolResultContent.Document] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[WebFetchToolResultContent.Document])
  }

  private implicit val webFetchSuccessFormat: OFormat[WebFetchToolResultContent.Success] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[WebFetchToolResultContent.Success])
  }

  private implicit val webFetchErrorFormat: Format[WebFetchToolResultContent.Error] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[WebFetchToolResultContent.Error])
  }

  implicit lazy val webFetchToolResultContentReads: Reads[WebFetchToolResultContent] = {
    case obj: JsObject =>
      (obj \ "type").asOpt[String] match {
        case Some("web_fetch_tool_result_error") =>
          obj.validate[WebFetchToolResultContent.Error]
        case _ =>
          obj.validate[WebFetchToolResultContent.Success]
      }
    case _ =>
      JsError("Expected object for web fetch tool result content")
  }

  implicit lazy val webFetchToolResultContentWrites: Writes[WebFetchToolResultContent] = {
    case success: WebFetchToolResultContent.Success =>
      Json.toJson(success)(webFetchSuccessFormat)
    case error: WebFetchToolResultContent.Error =>
      Json.toJson(error)(webFetchErrorFormat)
  }

  implicit lazy val webFetchToolResultContentFormat: Format[WebFetchToolResultContent] =
    Format(webFetchToolResultContentReads, webFetchToolResultContentWrites)

  private val webFetchToolResultBlockFormat: OFormat[WebFetchToolResultBlock] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[WebFetchToolResultBlock]
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
    // Note: Json.using[Json.WithDefaultValues] does NOT respect JsonConfiguration(SnakeCase),
    // so we use explicit snake_case paths with readWithDefault/readNullable
    val reads: Reads[TextEditorCodeExecutionToolResultContent.ReplaceResult] = (
      (__ \ "lines").readWithDefault[Seq[String]](Nil) and
        (__ \ "new_lines").readNullable[Int] and
        (__ \ "new_start").readNullable[Int] and
        (__ \ "old_lines").readNullable[Int] and
        (__ \ "old_start").readNullable[Int]
    )(TextEditorCodeExecutionToolResultContent.ReplaceResult.apply _)

    val writes: OWrites[TextEditorCodeExecutionToolResultContent.ReplaceResult] = OWrites {
      r =>
        var obj = Json.obj()
        if (r.lines.nonEmpty) obj = obj + ("lines" -> Json.toJson(r.lines))
        r.newLines.foreach(v => obj = obj + ("new_lines" -> JsNumber(v)))
        r.newStart.foreach(v => obj = obj + ("new_start" -> JsNumber(v)))
        r.oldLines.foreach(v => obj = obj + ("old_lines" -> JsNumber(v)))
        r.oldStart.foreach(v => obj = obj + ("old_start" -> JsNumber(v)))
        obj
    }

    OFormat(reads, writes)
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

      case "web_fetch_tool_result" =>
        json.validate[WebFetchToolResultBlock](webFetchToolResultBlockFormat)

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

    case x: WebFetchToolResultBlock =>
      Json.toJsObject(x)(webFetchToolResultBlockFormat)

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
          citations = if (x.citations.getOrElse(false)) Some(CitationsFlag(true)) else None
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
          citations = if (x.citations.getOrElse(false)) Some(CitationsFlag(true)) else None
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
          citations = if (x.citations.getOrElse(false)) Some(CitationsFlag(true)) else None
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

  implicit lazy val outputEffortFormat: Format[OutputEffort] =
    JsonUtil.enumFormat[OutputEffort](OutputEffort.values: _*)
  implicit lazy val outputConfigFormat: Format[OutputConfig] =
    Json.format[OutputConfig]

  implicit lazy val speedFormat: Format[Speed] =
    JsonUtil.enumFormat[Speed](Speed.values: _*)

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
    val reads: Reads[MCPToolConfiguration] = (
      (__ \ "allowed_tools").readWithDefault[Seq[String]](Nil) and
        (__ \ "enabled").readNullable[Boolean]
    )(MCPToolConfiguration.apply _)

    val writes: OWrites[MCPToolConfiguration] = OWrites { config =>
      var obj = Json.obj()
      if (config.allowedTools.nonEmpty) {
        obj = obj + ("allowed_tools" -> Json.toJson(config.allowedTools))
      }
      config.enabled.foreach(e => obj = obj + ("enabled" -> JsBoolean(e)))
      obj
    }

    Format(reads, writes)
  }

  private val mcpServerURLDefinitionFormat: OFormat[MCPServerURLDefinition] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    formatWithType(Json.format[MCPServerURLDefinition])
  }

  implicit val mcpServerURLDefinitionWrites: OWrites[MCPServerURLDefinition] =
    mcpServerURLDefinitionFormat

  implicit lazy val mcpToolConfigFormat: OFormat[MCPToolConfig] = {
    val reads: Reads[MCPToolConfig] = (
      (__ \ "enabled").readNullable[Boolean] and
        (__ \ "defer_loading").readNullable[Boolean]
    )(MCPToolConfig.apply _)

    val writes: OWrites[MCPToolConfig] = OWrites { config =>
      var obj = Json.obj()
      config.enabled.foreach(e => obj = obj + ("enabled" -> JsBoolean(e)))
      config.deferLoading.foreach(d => obj = obj + ("defer_loading" -> JsBoolean(d)))
      obj
    }

    OFormat(reads, writes)
  }

  implicit lazy val mcpToolsetFormat: OFormat[MCPToolset] = {
    val reads: Reads[MCPToolset] = (
      (__ \ "mcp_server_name").read[String] and
        (__ \ "default_config").readNullable[MCPToolConfig] and
        (__ \ "configs").readWithDefault[Map[String, MCPToolConfig]](Map.empty) and
        (__ \ "cache_control").readNullable[CacheControl]
    )(MCPToolset.apply _)

    val writes: OWrites[MCPToolset] = OWrites { toolset =>
      var obj = Json.obj("mcp_server_name" -> toolset.mcpServerName)
      toolset.defaultConfig.foreach(c => obj = obj + ("default_config" -> Json.toJson(c)))
      if (toolset.configs.nonEmpty) obj = obj + ("configs" -> Json.toJson(toolset.configs))
      toolset.cacheControl.foreach(c => obj = obj + ("cache_control" -> Json.toJson(c)))
      obj
    }

    formatWithType(OFormat(reads, writes))
  }

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
      case t: MCPToolset        => Json.toJsObject(t)(mcpToolsetFormat)
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

      case (Some("mcp_toolset"), _) =>
        json.validate[MCPToolset](mcpToolsetFormat)

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

  // Output Format
  implicit lazy val outputFormatFormat: OFormat[OutputFormat] = {
    val jsonSchemaFormatReads: Reads[OutputFormat.JsonSchemaFormat] = (json: JsValue) =>
      (json \ "schema").validate[JsonSchema].map { schema =>
        OutputFormat.JsonSchemaFormat(schema)
      }

    val jsonSchemaFormatWrites: OWrites[OutputFormat.JsonSchemaFormat] = OWrites { format =>
      Json.obj(
        "schema" -> Json.toJson(format.schema)(jsonSchemaFormat)
      )
    }

    val reads: Reads[OutputFormat] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "json_schema" => jsonSchemaFormatReads.reads(json)
        case other         => JsError(s"Unknown output format type: $other")
      }
    }

    val writes: OWrites[OutputFormat] = OWrites { case format: OutputFormat.JsonSchemaFormat =>
      jsonSchemaFormatWrites.writes(format)
    }

    formatWithType(OFormat(reads, writes))
  }

  // ============================================================================
  // Managed Agents — shared types (Agents block)
  // ============================================================================

  // Serialized as an object with a `type` discriminator, e.g. {"type":"always_allow"}.
  implicit lazy val permissionPolicyFormat: Format[PermissionPolicy] = {
    val reads: Reads[PermissionPolicy] = (__ \ "type").read[String].flatMap { t =>
      Reads { _ =>
        PermissionPolicy.values
          .find(_.toString == t)
          .map(JsSuccess(_))
          .getOrElse(JsError(s"Unknown permission policy type: $t"))
      }
    }
    val writes: OWrites[PermissionPolicy] =
      OWrites(pp => Json.obj("type" -> pp.toString))
    OFormat(reads, writes)
  }

  implicit lazy val agentToolConfigFormat: OFormat[AgentToolConfig] = {
    val reads: Reads[AgentToolConfig] = (
      (__ \ "name").readNullable[String] and
        (__ \ "enabled").readNullable[Boolean] and
        (__ \ "permission_policy").readNullable[PermissionPolicy]
    )(AgentToolConfig.apply _)

    val writes: OWrites[AgentToolConfig] = OWrites { c =>
      var obj = Json.obj()
      c.name.foreach(n => obj = obj + ("name" -> JsString(n)))
      c.enabled.foreach(e => obj = obj + ("enabled" -> JsBoolean(e)))
      c.permissionPolicy.foreach(p => obj = obj + ("permission_policy" -> Json.toJson(p)))
      obj
    }
    OFormat(reads, writes)
  }

  implicit lazy val agentToolFormat: Format[AgentTool] = {
    val reads: Reads[AgentTool] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "agent_toolset_20260401" =>
          for {
            configs <- (json \ "configs")
              .validateOpt[Seq[AgentToolConfig]]
              .map(_.getOrElse(Nil))
            defaultConfig <- (json \ "default_config").validateOpt[AgentToolConfig]
          } yield AgentTool.Toolset(configs, defaultConfig)
        case "mcp_toolset" =>
          for {
            serverName <- (json \ "mcp_server_name").validate[String]
            configs <- (json \ "configs")
              .validateOpt[Seq[AgentToolConfig]]
              .map(_.getOrElse(Nil))
            defaultConfig <- (json \ "default_config").validateOpt[AgentToolConfig]
          } yield AgentTool.McpToolset(serverName, configs, defaultConfig)
        case "custom" =>
          json.validate[CustomTool](customToolFormat).map(AgentTool.Custom.apply)
        case other => JsError(s"Unknown agent tool type: $other")
      }
    }

    val writes: OWrites[AgentTool] = OWrites {
      case t: AgentTool.Toolset =>
        var obj = Json.obj("type" -> t.`type`)
        if (t.configs.nonEmpty) obj = obj + ("configs" -> Json.toJson(t.configs))
        t.defaultConfig.foreach(c => obj = obj + ("default_config" -> Json.toJson(c)))
        obj
      case t: AgentTool.McpToolset =>
        var obj = Json.obj("type" -> t.`type`, "mcp_server_name" -> t.mcpServerName)
        if (t.configs.nonEmpty) obj = obj + ("configs" -> Json.toJson(t.configs))
        t.defaultConfig.foreach(c => obj = obj + ("default_config" -> Json.toJson(c)))
        obj
      case t: AgentTool.Custom =>
        Json.toJsObject(t.tool)(customToolFormat) + ("type" -> JsString(t.`type`))
    }
    OFormat(reads, writes)
  }

  // Request accepts a bare string or {id, speed}; response always returns the object.
  implicit lazy val agentModelConfigFormat: Format[AgentModelConfig] = {
    val reads: Reads[AgentModelConfig] = Reads {
      case JsString(id) => JsSuccess(AgentModelConfig(id, None))
      case json: JsObject =>
        for {
          id <- (json \ "id").validate[String]
          speed <- (json \ "speed").validateOpt[Speed]
        } yield AgentModelConfig(id, speed)
      case other => JsError(s"Expected model id string or object, got: $other")
    }
    val writes: Writes[AgentModelConfig] = Writes { c =>
      c.speed match {
        case Some(speed) => Json.obj("id" -> c.id, "speed" -> Json.toJson(speed))
        case None        => JsString(c.id)
      }
    }
    Format(reads, writes)
  }

  implicit lazy val multiagentMemberFormat: Format[MultiagentMember] = {
    val reads: Reads[MultiagentMember] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "agent" =>
          for {
            id <- (json \ "id").validate[String]
            version <- (json \ "version").validateOpt[Int]
          } yield MultiagentMember.AgentRef(id, version)
        case "self" => JsSuccess(MultiagentMember.SelfRef)
        case other  => JsError(s"Unknown multiagent member type: $other")
      }
    }
    val writes: OWrites[MultiagentMember] = OWrites {
      case m: MultiagentMember.AgentRef =>
        var obj = Json.obj("type" -> "agent", "id" -> m.id)
        m.version.foreach(v => obj = obj + ("version" -> JsNumber(v)))
        obj
      case MultiagentMember.SelfRef => Json.obj("type" -> "self")
    }
    Format(reads, writes)
  }

  implicit lazy val multiagentFormat: Format[Multiagent] = {
    val reads: Reads[Multiagent] =
      (__ \ "agents").read[Seq[MultiagentMember]].map(Multiagent(_))
    val writes: OWrites[Multiagent] =
      OWrites(m => Json.obj("type" -> m.`type`, "agents" -> Json.toJson(m.agents)))
    Format(reads, writes)
  }

  implicit lazy val agentFormat: Format[Agent] = {
    val reads: Reads[Agent] = (
      (__ \ "id").read[String] and
        (__ \ "name").read[String] and
        (__ \ "model").read[AgentModelConfig] and
        (__ \ "version").read[Int] and
        (__ \ "description").readNullable[String] and
        (__ \ "system").readNullable[String] and
        (__ \ "tools").readWithDefault[Seq[AgentTool]](Nil) and
        (__ \ "mcp_servers").readWithDefault[Seq[MCPServerURLDefinition]](Nil)(
          Reads.seq(mcpServerURLDefinitionFormat)
        ) and
        (__ \ "skills").readWithDefault[Seq[SkillParams]](Nil) and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "multiagent").readNullable[Multiagent] and
        (__ \ "created_at").readNullable[String] and
        (__ \ "updated_at").readNullable[String] and
        (__ \ "archived_at").readNullable[String]
    )(Agent.apply _)

    val writes: OWrites[Agent] = OWrites { a =>
      var obj = Json.obj(
        "type" -> a.`type`,
        "id" -> a.id,
        "name" -> a.name,
        "model" -> Json.toJson(a.model),
        "version" -> a.version
      )
      a.description.foreach(d => obj = obj + ("description" -> JsString(d)))
      a.system.foreach(s => obj = obj + ("system" -> JsString(s)))
      if (a.tools.nonEmpty) obj = obj + ("tools" -> Json.toJson(a.tools))
      if (a.mcpServers.nonEmpty)
        obj = obj + ("mcp_servers" -> Json.toJson(a.mcpServers)(
          Writes.seq(mcpServerURLDefinitionWrites)
        ))
      if (a.skills.nonEmpty) obj = obj + ("skills" -> Json.toJson(a.skills))
      if (a.metadata.nonEmpty) obj = obj + ("metadata" -> Json.toJson(a.metadata))
      a.multiagent.foreach(m => obj = obj + ("multiagent" -> Json.toJson(m)))
      a.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
      a.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
      a.archivedAt.foreach(t => obj = obj + ("archived_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  implicit def pagedResponseFormat[T](
    implicit itemFormat: Format[T]
  ): Format[PagedResponse[T]] = {
    val reads: Reads[PagedResponse[T]] = (
      (__ \ "data").readWithDefault[Seq[T]](Nil)(Reads.seq(itemFormat)) and
        (__ \ "next_page").readNullable[String]
    )(PagedResponse.apply[T] _)
    val writes: OWrites[PagedResponse[T]] = OWrites { p =>
      var obj = Json.obj("data" -> Json.toJson(p.data)(Writes.seq(itemFormat)))
      p.nextPage.foreach(np => obj = obj + ("next_page" -> JsString(np)))
      obj
    }
    Format(reads, writes)
  }

  // ============================================================================
  // Managed Agents — environments
  // ============================================================================

  implicit lazy val environmentScopeFormat: Format[EnvironmentScope] =
    JsonUtil.enumFormat[EnvironmentScope](EnvironmentScope.values: _*)

  implicit lazy val networkingFormat: Format[Networking] = {
    val reads: Reads[Networking] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "unrestricted" => JsSuccess(Networking.Unrestricted)
        case "limited" =>
          for {
            allowMcp <- (json \ "allow_mcp_servers").validateOpt[Boolean]
            allowPkg <- (json \ "allow_package_managers").validateOpt[Boolean]
            hosts <- (json \ "allowed_hosts").validateOpt[Seq[String]].map(_.getOrElse(Nil))
          } yield Networking.Limited(allowMcp, allowPkg, hosts)
        case other => JsError(s"Unknown networking type: $other")
      }
    }
    val writes: OWrites[Networking] = OWrites {
      case Networking.Unrestricted => Json.obj("type" -> "unrestricted")
      case l: Networking.Limited =>
        var obj = Json.obj("type" -> "limited")
        l.allowMcpServers.foreach(b => obj = obj + ("allow_mcp_servers" -> JsBoolean(b)))
        l.allowPackageManagers.foreach(b =>
          obj = obj + ("allow_package_managers" -> JsBoolean(b))
        )
        if (l.allowedHosts.nonEmpty)
          obj = obj + ("allowed_hosts" -> Json.toJson(l.allowedHosts))
        obj
    }
    Format(reads, writes)
  }

  implicit lazy val packagesFormat: Format[Packages] = {
    val reads: Reads[Packages] = (
      (__ \ "apt").readWithDefault[Seq[String]](Nil) and
        (__ \ "cargo").readWithDefault[Seq[String]](Nil) and
        (__ \ "gem").readWithDefault[Seq[String]](Nil) and
        (__ \ "go").readWithDefault[Seq[String]](Nil) and
        (__ \ "npm").readWithDefault[Seq[String]](Nil) and
        (__ \ "pip").readWithDefault[Seq[String]](Nil)
    )(Packages.apply _)
    val writes: OWrites[Packages] = OWrites { p =>
      var obj = Json.obj("type" -> "packages")
      if (p.apt.nonEmpty) obj = obj + ("apt" -> Json.toJson(p.apt))
      if (p.cargo.nonEmpty) obj = obj + ("cargo" -> Json.toJson(p.cargo))
      if (p.gem.nonEmpty) obj = obj + ("gem" -> Json.toJson(p.gem))
      if (p.go.nonEmpty) obj = obj + ("go" -> Json.toJson(p.go))
      if (p.npm.nonEmpty) obj = obj + ("npm" -> Json.toJson(p.npm))
      if (p.pip.nonEmpty) obj = obj + ("pip" -> Json.toJson(p.pip))
      obj
    }
    Format(reads, writes)
  }

  implicit lazy val environmentConfigFormat: Format[EnvironmentConfig] = {
    val reads: Reads[EnvironmentConfig] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "cloud" =>
          for {
            networking <- (json \ "networking").validateOpt[Networking]
            packages <- (json \ "packages").validateOpt[Packages]
          } yield EnvironmentConfig.Cloud(networking, packages)
        case "self_hosted" => JsSuccess(EnvironmentConfig.SelfHosted)
        case other         => JsError(s"Unknown environment config type: $other")
      }
    }
    val writes: OWrites[EnvironmentConfig] = OWrites {
      case c: EnvironmentConfig.Cloud =>
        var obj = Json.obj("type" -> "cloud")
        c.networking.foreach(n => obj = obj + ("networking" -> Json.toJson(n)))
        c.packages.foreach(p => obj = obj + ("packages" -> Json.toJson(p)))
        obj
      case EnvironmentConfig.SelfHosted => Json.obj("type" -> "self_hosted")
    }
    Format(reads, writes)
  }

  implicit lazy val environmentFormat: Format[Environment] = {
    val reads: Reads[Environment] = (
      (__ \ "id").read[String] and
        (__ \ "name").read[String] and
        (__ \ "config").read[EnvironmentConfig] and
        (__ \ "description").readNullable[String] and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "scope").readNullable[EnvironmentScope] and
        (__ \ "created_at").readNullable[String] and
        (__ \ "updated_at").readNullable[String] and
        (__ \ "archived_at").readNullable[String]
    )(Environment.apply _)

    val writes: OWrites[Environment] = OWrites { e =>
      var obj = Json.obj(
        "type" -> e.`type`,
        "id" -> e.id,
        "name" -> e.name,
        "config" -> Json.toJson(e.config)
      )
      e.description.foreach(d => obj = obj + ("description" -> JsString(d)))
      if (e.metadata.nonEmpty) obj = obj + ("metadata" -> Json.toJson(e.metadata))
      e.scope.foreach(s => obj = obj + ("scope" -> Json.toJson(s)))
      e.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
      e.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
      e.archivedAt.foreach(t => obj = obj + ("archived_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  implicit lazy val environmentDeleteResponseFormat: Format[EnvironmentDeleteResponse] = {
    val reads: Reads[EnvironmentDeleteResponse] =
      (__ \ "id").read[String].map(EnvironmentDeleteResponse(_))
    val writes: OWrites[EnvironmentDeleteResponse] =
      OWrites(r => Json.obj("id" -> r.id, "type" -> r.`type`))
    Format(reads, writes)
  }

  // ============================================================================
  // Managed Agents — environment work (self-hosted worker queue)
  // ============================================================================

  implicit lazy val workStateFormat: Format[WorkState] =
    JsonUtil.enumFormat[WorkState](WorkState.values: _*)

  implicit lazy val sessionWorkDataFormat: Format[SessionWorkData] = {
    val reads: Reads[SessionWorkData] = (__ \ "id").read[String].map(SessionWorkData(_))
    val writes: OWrites[SessionWorkData] =
      OWrites(d => Json.obj("id" -> d.id, "type" -> d.`type`))
    Format(reads, writes)
  }

  implicit lazy val selfHostedWorkFormat: Format[SelfHostedWork] = {
    val reads: Reads[SelfHostedWork] = (
      (__ \ "id").read[String] and
        (__ \ "data").read[SessionWorkData] and
        (__ \ "environment_id").read[String] and
        (__ \ "state").read[WorkState] and
        (__ \ "acknowledged_at").readNullable[String] and
        (__ \ "created_at").readNullable[String] and
        (__ \ "latest_heartbeat_at").readNullable[String] and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "started_at").readNullable[String] and
        (__ \ "stop_requested_at").readNullable[String] and
        (__ \ "stopped_at").readNullable[String]
    )(SelfHostedWork.apply _)

    val writes: OWrites[SelfHostedWork] = OWrites { w =>
      var obj = Json.obj(
        "type" -> w.`type`,
        "id" -> w.id,
        "data" -> Json.toJson(w.data),
        "environment_id" -> w.environmentId,
        "state" -> Json.toJson(w.state)
      )
      w.acknowledgedAt.foreach(t => obj = obj + ("acknowledged_at" -> JsString(t)))
      w.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
      w.latestHeartbeatAt.foreach(t => obj = obj + ("latest_heartbeat_at" -> JsString(t)))
      if (w.metadata.nonEmpty) obj = obj + ("metadata" -> Json.toJson(w.metadata))
      w.startedAt.foreach(t => obj = obj + ("started_at" -> JsString(t)))
      w.stopRequestedAt.foreach(t => obj = obj + ("stop_requested_at" -> JsString(t)))
      w.stoppedAt.foreach(t => obj = obj + ("stopped_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  implicit lazy val workHeartbeatResponseFormat: Format[WorkHeartbeatResponse] = {
    val reads: Reads[WorkHeartbeatResponse] = (
      (__ \ "last_heartbeat").read[String] and
        (__ \ "lease_extended").read[Boolean] and
        (__ \ "state").read[WorkState] and
        (__ \ "ttl_seconds").read[Int]
    )(WorkHeartbeatResponse.apply _)
    val writes: OWrites[WorkHeartbeatResponse] = OWrites { h =>
      Json.obj(
        "type" -> h.`type`,
        "last_heartbeat" -> h.lastHeartbeat,
        "lease_extended" -> h.leaseExtended,
        "state" -> Json.toJson(h.state),
        "ttl_seconds" -> h.ttlSeconds
      )
    }
    Format(reads, writes)
  }

  implicit lazy val workQueueStatsFormat: Format[WorkQueueStats] = {
    val reads: Reads[WorkQueueStats] = (
      (__ \ "depth").read[Int] and
        (__ \ "pending").read[Int] and
        (__ \ "workers_polling").read[Int] and
        (__ \ "oldest_queued_at").readNullable[String]
    )(WorkQueueStats.apply _)
    val writes: OWrites[WorkQueueStats] = OWrites { s =>
      var obj = Json.obj(
        "type" -> s.`type`,
        "depth" -> s.depth,
        "pending" -> s.pending,
        "workers_polling" -> s.workersPolling
      )
      s.oldestQueuedAt.foreach(t => obj = obj + ("oldest_queued_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  // ============================================================================
  // Managed Agents — sessions
  // ============================================================================

  implicit lazy val sessionStatusFormat: Format[SessionStatus] =
    JsonUtil.enumFormat[SessionStatus](SessionStatus.values: _*)

  implicit lazy val memoryStoreAccessFormat: Format[MemoryStoreAccess] =
    JsonUtil.enumFormat[MemoryStoreAccess](MemoryStoreAccess.values: _*)

  implicit lazy val sessionThreadStatusFormat: Format[SessionThreadStatus] =
    JsonUtil.enumFormat[SessionThreadStatus](SessionThreadStatus.values: _*)

  implicit lazy val checkoutFormat: Format[Checkout] = {
    val reads: Reads[Checkout] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "branch" => (json \ "name").validate[String].map(Checkout.Branch(_))
        case "commit" => (json \ "sha").validate[String].map(Checkout.Commit(_))
        case other    => JsError(s"Unknown checkout type: $other")
      }
    }
    val writes: OWrites[Checkout] = OWrites {
      case b: Checkout.Branch => Json.obj("type" -> b.`type`, "name" -> b.name)
      case c: Checkout.Commit => Json.obj("type" -> c.`type`, "sha" -> c.sha)
    }
    Format(reads, writes)
  }

  implicit lazy val sessionResourceFormat: Format[SessionResource] = {
    val reads: Reads[SessionResource] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "file" =>
          for {
            fileId <- (json \ "file_id").validate[String]
            mountPath <- (json \ "mount_path").validateOpt[String]
            id <- (json \ "id").validateOpt[String]
            createdAt <- (json \ "created_at").validateOpt[String]
            updatedAt <- (json \ "updated_at").validateOpt[String]
          } yield SessionResource.File(fileId, mountPath, id, createdAt, updatedAt)
        case "github_repository" =>
          for {
            url <- (json \ "url").validate[String]
            token <- (json \ "authorization_token").validateOpt[String]
            checkout <- (json \ "checkout").validateOpt[Checkout]
            mountPath <- (json \ "mount_path").validateOpt[String]
            id <- (json \ "id").validateOpt[String]
            createdAt <- (json \ "created_at").validateOpt[String]
            updatedAt <- (json \ "updated_at").validateOpt[String]
          } yield SessionResource.GithubRepository(
            url,
            token,
            checkout,
            mountPath,
            id,
            createdAt,
            updatedAt
          )
        case "memory_store" =>
          for {
            storeId <- (json \ "memory_store_id").validate[String]
            access <- (json \ "access").validateOpt[MemoryStoreAccess]
            instructions <- (json \ "instructions").validateOpt[String]
            name <- (json \ "name").validateOpt[String]
            description <- (json \ "description").validateOpt[String]
            mountPath <- (json \ "mount_path").validateOpt[String]
          } yield SessionResource.MemoryStore(
            storeId,
            access,
            instructions,
            name,
            description,
            mountPath
          )
        case other => JsError(s"Unknown session resource type: $other")
      }
    }
    val writes: OWrites[SessionResource] = OWrites {
      case r: SessionResource.File =>
        var obj = Json.obj("type" -> r.`type`, "file_id" -> r.fileId)
        r.mountPath.foreach(p => obj = obj + ("mount_path" -> JsString(p)))
        r.id.foreach(i => obj = obj + ("id" -> JsString(i)))
        r.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
        r.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
        obj
      case r: SessionResource.GithubRepository =>
        var obj = Json.obj("type" -> r.`type`, "url" -> r.url)
        r.authorizationToken.foreach(t => obj = obj + ("authorization_token" -> JsString(t)))
        r.checkout.foreach(c => obj = obj + ("checkout" -> Json.toJson(c)))
        r.mountPath.foreach(p => obj = obj + ("mount_path" -> JsString(p)))
        r.id.foreach(i => obj = obj + ("id" -> JsString(i)))
        r.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
        r.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
        obj
      case r: SessionResource.MemoryStore =>
        var obj = Json.obj("type" -> r.`type`, "memory_store_id" -> r.memoryStoreId)
        r.access.foreach(a => obj = obj + ("access" -> Json.toJson(a)))
        r.instructions.foreach(i => obj = obj + ("instructions" -> JsString(i)))
        r.name.foreach(n => obj = obj + ("name" -> JsString(n)))
        r.description.foreach(d => obj = obj + ("description" -> JsString(d)))
        r.mountPath.foreach(p => obj = obj + ("mount_path" -> JsString(p)))
        obj
    }
    Format(reads, writes)
  }

  implicit lazy val sessionFormat: Format[Session] = {
    val reads: Reads[Session] = (
      (__ \ "id").read[String] and
        (__ \ "status").read[SessionStatus] and
        (__ \ "agent").read[Agent] and
        (__ \ "environment_id").read[String] and
        (__ \ "title").readNullable[String] and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "resources").readWithDefault[Seq[SessionResource]](Nil) and
        (__ \ "vault_ids").readWithDefault[Seq[String]](Nil) and
        (__ \ "deployment_id").readNullable[String] and
        (__ \ "created_at").readNullable[String] and
        (__ \ "updated_at").readNullable[String] and
        (__ \ "archived_at").readNullable[String]
    )(Session.apply _)

    val writes: OWrites[Session] = OWrites { s =>
      var obj = Json.obj(
        "type" -> s.`type`,
        "id" -> s.id,
        "status" -> Json.toJson(s.status),
        "agent" -> Json.toJson(s.agent),
        "environment_id" -> s.environmentId
      )
      s.title.foreach(t => obj = obj + ("title" -> JsString(t)))
      if (s.metadata.nonEmpty) obj = obj + ("metadata" -> Json.toJson(s.metadata))
      if (s.resources.nonEmpty) obj = obj + ("resources" -> Json.toJson(s.resources))
      if (s.vaultIds.nonEmpty) obj = obj + ("vault_ids" -> Json.toJson(s.vaultIds))
      s.deploymentId.foreach(d => obj = obj + ("deployment_id" -> JsString(d)))
      s.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
      s.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
      s.archivedAt.foreach(t => obj = obj + ("archived_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  implicit lazy val sessionDeleteResponseFormat: Format[SessionDeleteResponse] = {
    val reads: Reads[SessionDeleteResponse] =
      (__ \ "id").read[String].map(SessionDeleteResponse(_))
    val writes: OWrites[SessionDeleteResponse] =
      OWrites(r => Json.obj("id" -> r.id, "type" -> r.`type`))
    Format(reads, writes)
  }

  implicit lazy val sessionThreadFormat: Format[SessionThread] = {
    val reads: Reads[SessionThread] = (
      (__ \ "id").read[String] and
        (__ \ "status").read[SessionThreadStatus] and
        (__ \ "session_id").readNullable[String] and
        (__ \ "agent_id").readNullable[String] and
        (__ \ "parent_thread_id").readNullable[String] and
        (__ \ "created_at").readNullable[String] and
        (__ \ "updated_at").readNullable[String] and
        (__ \ "archived_at").readNullable[String]
    )(SessionThread.apply _)
    val writes: OWrites[SessionThread] = OWrites { t =>
      var obj = Json.obj("type" -> t.`type`, "id" -> t.id, "status" -> Json.toJson(t.status))
      t.sessionId.foreach(v => obj = obj + ("session_id" -> JsString(v)))
      t.agentId.foreach(v => obj = obj + ("agent_id" -> JsString(v)))
      t.parentThreadId.foreach(v => obj = obj + ("parent_thread_id" -> JsString(v)))
      t.createdAt.foreach(v => obj = obj + ("created_at" -> JsString(v)))
      t.updatedAt.foreach(v => obj = obj + ("updated_at" -> JsString(v)))
      t.archivedAt.foreach(v => obj = obj + ("archived_at" -> JsString(v)))
      obj
    }
    Format(reads, writes)
  }

  // Received events keep the raw payload; only the common envelope fields are typed.
  implicit lazy val sessionEventEnvelopeFormat: Format[SessionEventEnvelope] = {
    val reads: Reads[SessionEventEnvelope] = Reads {
      case obj: JsObject =>
        JsSuccess(
          SessionEventEnvelope(
            `type` = (obj \ "type").asOpt[String].getOrElse(""),
            id = (obj \ "id").asOpt[String],
            processedAt = (obj \ "processed_at").asOpt[String],
            raw = obj
          )
        )
      case other => JsError(s"Expected a session event object, got: $other")
    }
    val writes: OWrites[SessionEventEnvelope] = OWrites(_.raw)
    Format(reads, writes)
  }

  lazy val outcomeRubricReads: Reads[OutcomeRubric] = Reads { json =>
    (json \ "type").validate[String].flatMap {
      case "text" => (json \ "content").validate[String].map(OutcomeRubric.Text(_))
      case "file" => (json \ "file_id").validate[String].map(OutcomeRubric.File(_))
      case other  => JsError(s"Unknown outcome rubric type: $other")
    }
  }

  implicit lazy val outcomeRubricWrites: OWrites[OutcomeRubric] = OWrites {
    case r: OutcomeRubric.Text => Json.obj("type" -> r.`type`, "content" -> r.content)
    case r: OutcomeRubric.File => Json.obj("type" -> r.`type`, "file_id" -> r.fileId)
  }

  // Send-events serializer.
  implicit lazy val sessionEventWrites: OWrites[SessionEvent] = OWrites {
    case e: SessionEvent.UserMessage =>
      Json.obj(
        "type" -> e.`type`,
        "content" -> Json.arr(Json.obj("type" -> "text", "text" -> e.text))
      )
    case SessionEvent.UserInterrupt =>
      Json.obj("type" -> SessionEvent.UserInterrupt.`type`)
    case e: SessionEvent.UserToolConfirmation =>
      var obj = Json.obj(
        "type" -> e.`type`,
        "tool_use_id" -> e.toolUseId,
        "result" -> (if (e.allow) "allow" else "deny")
      )
      e.denyMessage.foreach(m => obj = obj + ("deny_message" -> JsString(m)))
      obj
    case e: SessionEvent.UserCustomToolResult =>
      var obj = Json.obj(
        "type" -> e.`type`,
        "custom_tool_use_id" -> e.customToolUseId,
        "content" -> Json.arr(Json.obj("type" -> "text", "text" -> e.text))
      )
      e.isError.foreach(b => obj = obj + ("is_error" -> JsBoolean(b)))
      obj
    case e: SessionEvent.UserDefineOutcome =>
      var obj = Json.obj(
        "type" -> e.`type`,
        "description" -> e.description,
        "rubric" -> Json.toJson(e.rubric)
      )
      e.maxIterations.foreach(n => obj = obj + ("max_iterations" -> JsNumber(n)))
      obj
  }

  // ============================================================================
  // Managed Agents — deployments
  // ============================================================================

  implicit lazy val deploymentStatusFormat: Format[DeploymentStatus] =
    JsonUtil.enumFormat[DeploymentStatus](DeploymentStatus.values: _*)

  implicit lazy val agentReferenceFormat: Format[AgentReference] = {
    val reads: Reads[AgentReference] = (
      (__ \ "id").read[String] and
        (__ \ "version").readNullable[Int]
    )(AgentReference.apply _)
    val writes: OWrites[AgentReference] = OWrites { r =>
      var obj = Json.obj("type" -> r.`type`, "id" -> r.id)
      r.version.foreach(v => obj = obj + ("version" -> JsNumber(v)))
      obj
    }
    Format(reads, writes)
  }

  implicit lazy val deploymentPausedReasonFormat: Format[DeploymentPausedReason] = {
    val reads: Reads[DeploymentPausedReason] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "manual" => JsSuccess(DeploymentPausedReason.Manual)
        case "error" =>
          (json \ "error" \ "type").validate[String].map(DeploymentPausedReason.Error(_))
        case other => JsError(s"Unknown paused reason type: $other")
      }
    }
    val writes: OWrites[DeploymentPausedReason] = OWrites {
      case DeploymentPausedReason.Manual => Json.obj("type" -> "manual")
      case e: DeploymentPausedReason.Error =>
        Json.obj("type" -> "error", "error" -> Json.obj("type" -> e.errorType))
    }
    Format(reads, writes)
  }

  implicit lazy val scheduleFormat: Format[Schedule] = {
    val reads: Reads[Schedule] = (
      (__ \ "expression").read[String] and
        (__ \ "timezone").read[String] and
        (__ \ "last_run_at").readNullable[String] and
        (__ \ "upcoming_runs_at").readWithDefault[Seq[String]](Nil)
    )(Schedule.apply _)
    val writes: OWrites[Schedule] = OWrites { s =>
      var obj =
        Json.obj("type" -> s.`type`, "expression" -> s.expression, "timezone" -> s.timezone)
      s.lastRunAt.foreach(t => obj = obj + ("last_run_at" -> JsString(t)))
      if (s.upcomingRunsAt.nonEmpty)
        obj = obj + ("upcoming_runs_at" -> Json.toJson(s.upcomingRunsAt))
      obj
    }
    Format(reads, writes)
  }

  implicit lazy val deploymentInitialEventFormat: Format[DeploymentInitialEvent] = {
    val reads: Reads[DeploymentInitialEvent] = Reads { json =>
      def textOf: JsResult[String] =
        (json \ "content" \ 0 \ "text").validate[String]
      (json \ "type").validate[String].flatMap {
        case "user.message"   => textOf.map(DeploymentInitialEvent.UserMessage(_))
        case "system.message" => textOf.map(DeploymentInitialEvent.SystemMessage(_))
        case "user.define_outcome" =>
          for {
            description <- (json \ "description").validate[String]
            rubric <- (json \ "rubric").validate[OutcomeRubric](outcomeRubricReads)
            maxIter <- (json \ "max_iterations").validateOpt[Int]
          } yield DeploymentInitialEvent.UserDefineOutcome(description, rubric, maxIter)
        case other => JsError(s"Unknown deployment initial event type: $other")
      }
    }
    val writes: OWrites[DeploymentInitialEvent] = OWrites {
      case e: DeploymentInitialEvent.UserMessage =>
        Json.obj(
          "type" -> e.`type`,
          "content" -> Json.arr(Json.obj("type" -> "text", "text" -> e.text))
        )
      case e: DeploymentInitialEvent.SystemMessage =>
        Json.obj(
          "type" -> e.`type`,
          "content" -> Json.arr(Json.obj("type" -> "text", "text" -> e.text))
        )
      case e: DeploymentInitialEvent.UserDefineOutcome =>
        var obj = Json.obj(
          "type" -> e.`type`,
          "description" -> e.description,
          "rubric" -> Json.toJson(e.rubric)
        )
        e.maxIterations.foreach(n => obj = obj + ("max_iterations" -> JsNumber(n)))
        obj
    }
    Format(reads, writes)
  }

  implicit lazy val deploymentFormat: Format[Deployment] = {
    val reads: Reads[Deployment] = (
      (__ \ "id").read[String] and
        (__ \ "name").read[String] and
        (__ \ "agent").read[AgentReference] and
        (__ \ "environment_id").read[String] and
        (__ \ "status").read[DeploymentStatus] and
        (__ \ "initial_events").readWithDefault[Seq[DeploymentInitialEvent]](Nil) and
        (__ \ "description").readNullable[String] and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "resources").readWithDefault[Seq[SessionResource]](Nil) and
        (__ \ "schedule").readNullable[Schedule] and
        (__ \ "paused_reason").readNullable[DeploymentPausedReason] and
        (__ \ "vault_ids").readWithDefault[Seq[String]](Nil) and
        (__ \ "created_at").readNullable[String] and
        (__ \ "updated_at").readNullable[String] and
        (__ \ "archived_at").readNullable[String]
    )(Deployment.apply _)

    val writes: OWrites[Deployment] = OWrites { d =>
      var obj = Json.obj(
        "type" -> d.`type`,
        "id" -> d.id,
        "name" -> d.name,
        "agent" -> Json.toJson(d.agent),
        "environment_id" -> d.environmentId,
        "status" -> Json.toJson(d.status)
      )
      if (d.initialEvents.nonEmpty)
        obj = obj + ("initial_events" -> Json.toJson(d.initialEvents))
      d.description.foreach(v => obj = obj + ("description" -> JsString(v)))
      if (d.metadata.nonEmpty) obj = obj + ("metadata" -> Json.toJson(d.metadata))
      if (d.resources.nonEmpty) obj = obj + ("resources" -> Json.toJson(d.resources))
      d.schedule.foreach(s => obj = obj + ("schedule" -> Json.toJson(s)))
      d.pausedReason.foreach(p => obj = obj + ("paused_reason" -> Json.toJson(p)))
      if (d.vaultIds.nonEmpty) obj = obj + ("vault_ids" -> Json.toJson(d.vaultIds))
      d.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
      d.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
      d.archivedAt.foreach(t => obj = obj + ("archived_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  // ============================================================================
  // Managed Agents — vaults
  // ============================================================================

  implicit lazy val vaultFormat: Format[Vault] = {
    val reads: Reads[Vault] = (
      (__ \ "id").read[String] and
        (__ \ "display_name").read[String] and
        (__ \ "metadata").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "created_at").readNullable[String] and
        (__ \ "updated_at").readNullable[String] and
        (__ \ "archived_at").readNullable[String]
    )(Vault.apply _)
    val writes: OWrites[Vault] = OWrites { v =>
      var obj = Json.obj("type" -> v.`type`, "id" -> v.id, "display_name" -> v.displayName)
      if (v.metadata.nonEmpty) obj = obj + ("metadata" -> Json.toJson(v.metadata))
      v.createdAt.foreach(t => obj = obj + ("created_at" -> JsString(t)))
      v.updatedAt.foreach(t => obj = obj + ("updated_at" -> JsString(t)))
      v.archivedAt.foreach(t => obj = obj + ("archived_at" -> JsString(t)))
      obj
    }
    Format(reads, writes)
  }

  // ============================================================================
  // Managed Agents — credentials (create-side auth is write-only)
  // ============================================================================

  implicit lazy val tokenEndpointAuthWrites: OWrites[TokenEndpointAuth] = OWrites {
    case TokenEndpointAuth.None_ => Json.obj("type" -> "none")
    case a: TokenEndpointAuth.ClientSecretBasic =>
      Json.obj("type" -> "client_secret_basic", "client_secret" -> a.clientSecret)
    case a: TokenEndpointAuth.ClientSecretPost =>
      Json.obj("type" -> "client_secret_post", "client_secret" -> a.clientSecret)
  }

  implicit lazy val mcpOAuthRefreshWrites: OWrites[McpOAuthRefresh] = OWrites { r =>
    var obj = Json.obj(
      "client_id" -> r.clientId,
      "refresh_token" -> r.refreshToken,
      "token_endpoint" -> r.tokenEndpoint,
      "token_endpoint_auth" -> Json.toJson(r.tokenEndpointAuth)
    )
    r.scope.foreach(s => obj = obj + ("scope" -> JsString(s)))
    obj
  }

  implicit lazy val credentialNetworkingWrites: OWrites[CredentialNetworking] = OWrites {
    case CredentialNetworking.Unrestricted => Json.obj("type" -> "unrestricted")
    case n: CredentialNetworking.Limited =>
      var obj = Json.obj("type" -> "limited")
      if (n.allowedHosts.nonEmpty) obj = obj + ("allowed_hosts" -> Json.toJson(n.allowedHosts))
      obj
  }

  implicit lazy val credentialAuthWrites: OWrites[CredentialAuth] = OWrites {
    case a: CredentialAuth.McpOAuth =>
      var obj = Json.obj(
        "type" -> a.`type`,
        "access_token" -> a.accessToken,
        "mcp_server_url" -> a.mcpServerUrl
      )
      a.expiresAt.foreach(t => obj = obj + ("expires_at" -> JsString(t)))
      a.refresh.foreach(r => obj = obj + ("refresh" -> Json.toJson(r)))
      obj
    case a: CredentialAuth.StaticBearer =>
      Json.obj("type" -> a.`type`, "token" -> a.token, "mcp_server_url" -> a.mcpServerUrl)
    case a: CredentialAuth.EnvironmentVariable =>
      Json.obj(
        "type" -> a.`type`,
        "secret_name" -> a.secretName,
        "secret_value" -> a.secretValue,
        "networking" -> Json.toJson(a.networking)
      )
  }

  // Credential responses omit secrets; type the discriminator + common fields, keep raw.
  implicit lazy val credentialFormat: Format[Credential] = {
    val reads: Reads[Credential] = Reads {
      case obj: JsObject =>
        JsSuccess(
          Credential(
            id = (obj \ "id").asOpt[String].getOrElse(""),
            authType = (obj \ "auth" \ "type").asOpt[String].getOrElse(""),
            displayName = (obj \ "display_name").asOpt[String],
            mcpServerUrl = (obj \ "auth" \ "mcp_server_url").asOpt[String],
            expiresAt = (obj \ "auth" \ "expires_at").asOpt[String],
            createdAt = (obj \ "created_at").asOpt[String],
            updatedAt = (obj \ "updated_at").asOpt[String],
            archivedAt = (obj \ "archived_at").asOpt[String],
            raw = obj
          )
        )
      case other => JsError(s"Expected a credential object, got: $other")
    }
    val writes: OWrites[Credential] = OWrites(_.raw)
    Format(reads, writes)
  }

  // Deployment-run schema is unpublished; type common fields, keep the raw payload.
  implicit lazy val deploymentRunFormat: Format[DeploymentRun] = {
    val reads: Reads[DeploymentRun] = Reads {
      case obj: JsObject =>
        JsSuccess(
          DeploymentRun(
            id = (obj \ "id").asOpt[String],
            deploymentId = (obj \ "deployment_id").asOpt[String],
            sessionId = (obj \ "session_id").asOpt[String],
            status = (obj \ "status").asOpt[String],
            createdAt = (obj \ "created_at").asOpt[String],
            raw = obj
          )
        )
      case other => JsError(s"Expected a deployment run object, got: $other")
    }
    val writes: OWrites[DeploymentRun] = OWrites(_.raw)
    Format(reads, writes)
  }
}
