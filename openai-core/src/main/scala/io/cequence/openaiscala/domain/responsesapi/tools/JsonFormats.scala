package io.cequence.openaiscala.domain.responsesapi.tools

import java.{util => ju}
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.JsonUtil.{enumFormat, snakeEnumFormat}
import io.cequence.openaiscala.domain.responsesapi.{InputMessageContent, ModelStatus}
import io.cequence.openaiscala.JsonFormats.{formatWithType, jsonSchemaFormat}
import io.cequence.openaiscala.domain.responsesapi.tools._
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats.toolFormat
import io.cequence.openaiscala.domain.responsesapi.JsonFormats.modelStatusFormat
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra
import play.api.libs.functional.syntax._
import play.api.libs.json._
import io.cequence.openaiscala.domain.responsesapi.tools.ComputerToolAction._
import io.cequence.openaiscala.domain.responsesapi.tools.mcp._

object JsonFormats {

  // general/initial config
  private implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  private implicit lazy val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  private implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] =
    JsonUtil.StringAnyMapFormat

  ///////////
  // Tools //
  ///////////

  // file search tool
  implicit lazy val fileFilterComparisonOperatorFormat: Format[FileFilter.ComparisonOperator] =
    enumFormat[FileFilter.ComparisonOperator](FileFilter.ComparisonOperator.values: _*)

  implicit lazy val fileFilterCompoundOperatorFormat: Format[FileFilter.CompoundOperator] =
    enumFormat[FileFilter.CompoundOperator](FileFilter.CompoundOperator.values: _*)

  implicit lazy val fileFilterComparisonFilterFormat: Format[FileFilter.ComparisonFilter] = {
    implicit val anyFormat: Format[Any] = new Format[Any] {
      def reads(json: JsValue): JsResult[Any] =
        JsonUtil.toValue(json).map(JsSuccess(_)).getOrElse(JsError(s"Undefined value: $json"))
      def writes(o: Any): JsValue = JsonUtil.toJson(o)
    }

    Json.format[FileFilter.ComparisonFilter]
  }

  private lazy val lazyFileFilterCompoundFilterFormat: Format[FileFilter.CompoundFilter] =
    Json.format[FileFilter.CompoundFilter]

  // recursive format for FileFilter (which can be either ComparisonFilter or CompoundFilter)
  implicit lazy val fileFilterFormat: Format[FileFilter] = new Format[FileFilter] {
    private val comparisonOperators: Set[String] =
      FileFilter.ComparisonOperator.values.map(_.toString).toSet

    private val compoundOperators: Set[String] =
      FileFilter.CompoundOperator.values.map(_.toString).toSet

    def reads(json: JsValue): JsResult[FileFilter] = {
      (json \ "type").validate[String].flatMap {
        case t if comparisonOperators.contains(t) =>
          fileFilterComparisonFilterFormat.reads(json)
        case t if compoundOperators.contains(t) =>
          lazyFileFilterCompoundFilterFormat.reads(json)
        case other => JsError(s"Unsupported filter type: $other")
      }
    }

    def writes(filter: FileFilter): JsValue = filter match {
      case cf: FileFilter.ComparisonFilter => fileFilterComparisonFilterFormat.writes(cf)
      case cf: FileFilter.CompoundFilter   => lazyFileFilterCompoundFilterFormat.writes(cf)
    }
  }

  implicit lazy val fileSearchRankingOptionsFormat: Format[FileSearchRankingOptions] =
    Json.format[FileSearchRankingOptions]

  private implicit lazy val fileSearchToolFormat: OFormat[FileSearchTool] =
    (
      (__ \ "vector_store_ids").formatWithDefault[Seq[String]](Seq.empty[String]) and
        (__ \ "filters").formatNullable[FileFilter] and
        (__ \ "max_num_results").formatNullable[Int] and
        (__ \ "ranking_options").formatNullable[FileSearchRankingOptions]
    )(
      FileSearchTool.apply _,
      (x: FileSearchTool) => (x.vectorStoreIds, x.filters, x.maxNumResults, x.rankingOptions)
    )

  // function tool
  private implicit lazy val functionToolFormat: OFormat[FunctionTool] = {
    val reads = Json.reads[FunctionTool]

    val writes = new OWrites[FunctionTool] {
      override def writes(tool: FunctionTool): JsObject = {
        val parametersJson = if (tool.strict) {
          // Apply strict schema transformation
          val strictParams = OpenAIChatCompletionExtra.toStrictSchema(Left(tool.parameters))
          JsonUtil.toJson(strictParams).as[JsObject]
        } else {
          // Use standard JSON schema serialization
          jsonSchemaFormat.writes(tool.parameters)
        }

        val baseJson = Json.obj(
          "name" -> tool.name,
          "parameters" -> parametersJson,
          "strict" -> tool.strict
        )

        tool.description.fold(baseJson)(desc => baseJson + ("description" -> JsString(desc)))
      }
    }

    OFormat(reads, writes)
  }

  // web search tool
  implicit lazy val webSearchTypeFormat: Format[WebSearchType] =
    enumFormat[WebSearchType](WebSearchType.values: _*)

  implicit lazy val webSearchFiltersFormat: Format[WebSearchFilters] =
    Json.format[WebSearchFilters]

  implicit lazy val webSearchUserLocationFormat: Format[WebSearchUserLocation] =
    Json.format[WebSearchUserLocation]

  private implicit lazy val webSearchToolFormat: OFormat[WebSearchTool] =
    Json.format[WebSearchTool]

  // computer use tool
  private implicit lazy val computerUseToolFormat: OFormat[ComputerUseTool] =
    Json.format[ComputerUseTool]

  // MCP tool
  implicit lazy val mcpToolFilterFormat: Format[MCPToolFilter] =
    Json.format[MCPToolFilter]

  implicit lazy val mcpRequireApprovalSettingFormat: Format[MCPRequireApproval.Setting] =
    enumFormat[MCPRequireApproval.Setting](MCPRequireApproval.Setting.values: _*)

  private implicit lazy val mcpRequireApprovalFilterFormat: Format[MCPRequireApproval.Filter] =
    Json.format[MCPRequireApproval.Filter]

  implicit lazy val mcpRequireApprovalFormat: Format[MCPRequireApproval] =
    new Format[MCPRequireApproval] {
      def reads(json: JsValue): JsResult[MCPRequireApproval] = {
        json match {
          case JsString(value) =>
            value match {
              case "always" => JsSuccess(MCPRequireApproval.Setting.Always)
              case "never"  => JsSuccess(MCPRequireApproval.Setting.Never)
              case _        => JsError(s"Unknown MCPRequireApproval string value: $value")
            }
          case obj: JsObject => mcpRequireApprovalFilterFormat.reads(obj)
          case _             => JsError("Expected string or object for MCPRequireApproval")
        }
      }

      def writes(approval: MCPRequireApproval): JsValue = approval match {
        case setting: MCPRequireApproval.Setting => JsString(setting.toString)
        case filter: MCPRequireApproval.Filter => mcpRequireApprovalFilterFormat.writes(filter)
      }
    }

  private implicit lazy val mcpAllowedToolsFilterFormat: Format[MCPAllowedTools.Filter] =
    Json.format[MCPAllowedTools.Filter]

  implicit lazy val mcpAllowedToolsFormat: Format[MCPAllowedTools] =
    new Format[MCPAllowedTools] {
      def reads(json: JsValue): JsResult[MCPAllowedTools] = {
        json match {
          case JsArray(values) =>
            val names = values.toSeq.map(_.validate[String])
            val errors = names.collect { case JsError(e) => e }
            if (errors.nonEmpty) {
              JsError(errors.flatten)
            } else {
              val strings = names.collect { case JsSuccess(s, _) => s }
              JsSuccess(MCPAllowedTools.ToolNames(strings))
            }
          case obj: JsObject =>
            mcpAllowedToolsFilterFormat.reads(obj)
          case _ => JsError("Expected array or object for MCPAllowedTools")
        }
      }

      def writes(allowedTools: MCPAllowedTools): JsValue = allowedTools match {
        case MCPAllowedTools.ToolNames(names) => JsArray(names.map(JsString(_)))
        case filter: MCPAllowedTools.Filter   => mcpAllowedToolsFilterFormat.writes(filter)
      }
    }

  private implicit lazy val mcpServerToolFormat: OFormat[MCPTool] =
    Json.format[MCPTool]

  // code interpreter tool
  private implicit lazy val codeInterpreterContainerAutoFormat
    : OFormat[CodeInterpreterContainer.Auto] =
    Json.format[CodeInterpreterContainer.Auto]

  implicit lazy val codeInterpreterContainerFormat: Format[CodeInterpreterContainer] =
    new Format[CodeInterpreterContainer] {
      def reads(json: JsValue): JsResult[CodeInterpreterContainer] = {
        json match {
          case JsString(containerId) =>
            JsSuccess(CodeInterpreterContainer.ContainerId(containerId))
          case obj: JsObject =>
            codeInterpreterContainerAutoFormat.reads(obj)
          case _ => JsError("Expected string or object for CodeInterpreterContainer")
        }
      }

      def writes(container: CodeInterpreterContainer): JsValue = container match {
        case CodeInterpreterContainer.ContainerId(id) => JsString(id)
        case auto: CodeInterpreterContainer.Auto =>
          codeInterpreterContainerAutoFormat.writes(auto)
      }
    }

  private implicit lazy val codeInterpreterToolFormat: OFormat[CodeInterpreterTool] =
    Json.format[CodeInterpreterTool]

  // image generation tool
  implicit lazy val imageGenerationBackgroundFormat: Format[ImageGenerationBackground] =
    enumFormat[ImageGenerationBackground](
      ImageGenerationBackground.transparent,
      ImageGenerationBackground.opaque,
      ImageGenerationBackground.auto
    )

  implicit lazy val inputImageMaskFormat: Format[InputImageMask] =
    Json.format[InputImageMask]

  private implicit lazy val imageGenerationToolFormat: OFormat[ImageGenerationTool] =
    Json.format[ImageGenerationTool]

  // local shell tool
  private implicit lazy val localShellToolFormat: OFormat[LocalShellTool.type] =
    new OFormat[LocalShellTool.type] {
      def reads(json: JsValue): JsResult[LocalShellTool.type] = JsSuccess(LocalShellTool)
      def writes(tool: LocalShellTool.type): JsObject = Json.obj()
    }

  // custom tool
  implicit lazy val grammarSyntaxFormat: Format[GrammarSyntax] =
    enumFormat[GrammarSyntax](
      GrammarSyntax.lark,
      GrammarSyntax.regex
    )

  private implicit lazy val textFormatFormat: OFormat[TextFormat.type] =
    new OFormat[TextFormat.type] {
      def reads(json: JsValue): JsResult[TextFormat.type] = JsSuccess(TextFormat)
      def writes(format: TextFormat.type): JsObject = Json.obj()
    }

  private implicit lazy val grammarFormatFormat: OFormat[GrammarFormat] =
    Json.format[GrammarFormat]

  implicit lazy val customToolFormatFormat: Format[CustomToolFormat] =
    new Format[CustomToolFormat] {
      def reads(json: JsValue): JsResult[CustomToolFormat] = {
        (json \ "type").validate[String].flatMap {
          case "text"    => textFormatFormat.reads(json)
          case "grammar" => grammarFormatFormat.reads(json)
          case other     => JsError(s"Unsupported custom tool format type: $other")
        }
      }

      def writes(format: CustomToolFormat): JsValue = {
        val jsObject = format match {
          case TextFormat       => textFormatFormat.writes(TextFormat)
          case g: GrammarFormat => grammarFormatFormat.writes(g)
        }

        jsObject ++ Json.obj("type" -> format.`type`)
      }
    }

  private implicit lazy val customToolFormat: OFormat[CustomTool] =
    Json.format[CustomTool]

  private lazy val toolReads: Reads[Tool] = Reads { json =>
    (json \ "type").validate[String].flatMap {
      case "function"                      => functionToolFormat.reads(json)
      case "file_search"                   => fileSearchToolFormat.reads(json)
      case t if t.startsWith("web_search") => webSearchToolFormat.reads(json)
      case "computer_use_preview"          => computerUseToolFormat.reads(json)
      case "code_interpreter"              => codeInterpreterToolFormat.reads(json)
      case "image_generation"              => imageGenerationToolFormat.reads(json)
      case "local_shell"                   => localShellToolFormat.reads(json)
      case "custom"                        => customToolFormat.reads(json)
      case "mcp"                           => mcpServerToolFormat.reads(json)

      case other => JsError(s"Unsupported tool type: $other")
    }
  }

  private lazy val toolWrites: OWrites[Tool] = OWrites {
    case t: FunctionTool        => functionToolFormat.writes(t)
    case t: FileSearchTool      => fileSearchToolFormat.writes(t)
    case t: WebSearchTool       => webSearchToolFormat.writes(t)
    case t: ComputerUseTool     => computerUseToolFormat.writes(t)
    case t: CodeInterpreterTool => codeInterpreterToolFormat.writes(t)
    case t: ImageGenerationTool => imageGenerationToolFormat.writes(t)
    case LocalShellTool         => localShellToolFormat.writes(LocalShellTool)
    case t: CustomTool          => customToolFormat.writes(t)
    case t: MCPTool             => mcpServerToolFormat.writes(t)
  }

  implicit lazy val toolFormat: OFormat[Tool] =
    formatWithType(OFormat(toolReads, toolWrites))

  //////////////////
  // Tool Choices //
  //////////////////

  implicit lazy val toolChoiceModeFormat: Format[ToolChoice.Mode] = {
    enumFormat[ToolChoice.Mode](ToolChoice.Mode.values: _*)
  }

  private implicit lazy val toolChoiceAllowedToolsFormat: OFormat[ToolChoice.AllowedTools] =
    Json.format[ToolChoice.AllowedTools]

  implicit lazy val hostedToolTypeFormat: Format[ToolChoice.HostedToolType] =
    enumFormat[ToolChoice.HostedToolType](ToolChoice.HostedToolType.values: _*)

  private implicit lazy val hostedToolFormat: OFormat[ToolChoice.HostedTool] =
    Json.format[ToolChoice.HostedTool]

  private implicit lazy val toolChoiceFunctionToolFormat: OFormat[ToolChoice.FunctionTool] =
    Json.format[ToolChoice.FunctionTool]

  private implicit lazy val toolChoiceMCPToolFormat: OFormat[ToolChoice.MCPTool] =
    Json.format[ToolChoice.MCPTool]

  private implicit lazy val toolChoiceCustomToolFormat: OFormat[ToolChoice.CustomTool] =
    Json.format[ToolChoice.CustomTool]

  implicit lazy val toolChoiceFormat: Format[ToolChoice] = new Format[ToolChoice] {
    def reads(json: JsValue): JsResult[ToolChoice] = {
      json match {
        case JsString(value) =>
          value match {
            case "none"     => JsSuccess(ToolChoice.Mode.None)
            case "auto"     => JsSuccess(ToolChoice.Mode.Auto)
            case "required" => JsSuccess(ToolChoice.Mode.Required)
            case _          => JsError(s"Unknown ToolChoice string value: $value")
          }
        case obj: JsObject =>
          (obj \ "type").validate[String].flatMap {
            case "allowed_tools" => toolChoiceAllowedToolsFormat.reads(obj)
            case "function"      => toolChoiceFunctionToolFormat.reads(obj)
            case "mcp"           => toolChoiceMCPToolFormat.reads(obj)
            case "custom"        => toolChoiceCustomToolFormat.reads(obj)
            case t if ToolChoice.HostedToolType.values.map(_.toString).contains(t) =>
              hostedToolFormat.reads(obj)
            case other =>
              JsError(s"Unsupported ToolChoice type: $other")
          }
        case _ => JsError("Expected string or object for ToolChoice")
      }
    }

    def writes(toolChoice: ToolChoice): JsValue = toolChoice match {
      case mode: ToolChoice.Mode =>
        JsString(mode.toString)

      case at: ToolChoice.AllowedTools =>
        Json.toJsObject(at)(toolChoiceAllowedToolsFormat) ++
          Json.obj("type" -> "allowed_tools")

      case ft: ToolChoice.FunctionTool =>
        Json.toJsObject(ft)(toolChoiceFunctionToolFormat) ++
          Json.obj("type" -> "function")

      case mt: ToolChoice.MCPTool =>
        Json.toJsObject(mt)(toolChoiceMCPToolFormat) ++
          Json.obj("type" -> "mcp")

      case ct: ToolChoice.CustomTool =>
        Json.toJsObject(ct)(toolChoiceCustomToolFormat) ++
          Json.obj("type" -> "custom")

      case ht: ToolChoice.HostedTool =>
        Json.toJsObject(ht)(hostedToolFormat)
    }
  }

  ////////////////
  // Tool Calls //
  ////////////////

  implicit lazy val buttonClickFormat: Format[ButtonClick] =
    enumFormat[ButtonClick](ButtonClick.values: _*)

  implicit lazy val coordinateFormat: Format[Coordinate] = Json.format[Coordinate]

  private implicit lazy val clickFormat: OFormat[ComputerToolAction.Click] =
    Json.format[ComputerToolAction.Click]
  private implicit lazy val doubleClickFormat: OFormat[ComputerToolAction.DoubleClick] =
    Json.format[ComputerToolAction.DoubleClick]

  private implicit lazy val dragFormat: OFormat[ComputerToolAction.Drag] =
    Json.format[ComputerToolAction.Drag]

  private implicit lazy val keyPressFormat: OFormat[ComputerToolAction.KeyPress] =
    Json.format[ComputerToolAction.KeyPress]

  private implicit lazy val moveFormat: OFormat[ComputerToolAction.Move] =
    Json.format[ComputerToolAction.Move]
  private implicit lazy val scrollFormat: OFormat[ComputerToolAction.Scroll] =
    Json.format[ComputerToolAction.Scroll]
  private implicit lazy val typeFormat: OFormat[ComputerToolAction.Type] =
    Json.format[ComputerToolAction.Type]

  // Computer tool action formats
  implicit lazy val computerToolActionFormat: Format[ComputerToolAction] =
    new Format[ComputerToolAction] {
      def reads(json: JsValue): JsResult[ComputerToolAction] = {
        (json \ "type").validate[String].flatMap {
          case "click"        => clickFormat.reads(json)
          case "double_click" => doubleClickFormat.reads(json)
          case "drag"         => dragFormat.reads(json)
          case "keypress"     => keyPressFormat.reads(json)
          case "move"         => moveFormat.reads(json)
          case "screenshot"   => JsSuccess(ComputerToolAction.Screenshot)
          case "scroll"       => scrollFormat.reads(json)
          case "type"         => typeFormat.reads(json)
          case "wait"         => JsSuccess(ComputerToolAction.Wait)
          case other          => JsError(s"Unsupported computer tool action type: $other")
        }
      }

      def writes(action: ComputerToolAction): JsValue = {
        val jsObject = action match {
          case t: ComputerToolAction.Click       => clickFormat.writes(t)
          case t: ComputerToolAction.DoubleClick => doubleClickFormat.writes(t)
          case t: ComputerToolAction.Drag        => dragFormat.writes(t)
          case t: ComputerToolAction.KeyPress    => keyPressFormat.writes(t)
          case t: ComputerToolAction.Move        => moveFormat.writes(t)
          case ComputerToolAction.Screenshot     => Json.obj()
          case t: ComputerToolAction.Scroll      => scrollFormat.writes(t)
          case t: ComputerToolAction.Type        => typeFormat.writes(t)
          case ComputerToolAction.Wait           => Json.obj()
        }

        jsObject ++ Json.obj("type" -> action.`type`)
      }
    }

  // PendingSafetyCheck format
  implicit lazy val pendingSafetyCheckFormat: Format[PendingSafetyCheck] =
    Json.format[PendingSafetyCheck]

  // FileSearchResult format
  implicit lazy val fileSearchResultFormat: Format[FileSearchResult] =
    (
      (__ \ "attributes").formatWithDefault(Map.empty[String, Any]) and
        (__ \ "file_id").formatNullable[String] and
        (__ \ "filename").formatNullable[String] and
        (__ \ "score").formatNullable[Double] and
        (__ \ "text").formatNullable[String]
    )(
      FileSearchResult.apply _,
      // somehow FileSearchResult.unapply is not working in Scala3
      (x: FileSearchResult) =>
        (
          x.attributes,
          x.fileId,
          x.filename,
          x.score,
          x.text
        )
    )

  // Tool call formats for different types
  implicit lazy val functionToolCallFormat: OFormat[FunctionToolCall] =
    Json.format[FunctionToolCall]

  // Web search source format
  private implicit lazy val webSearchSourceReads: Reads[WebSearchSource] =
    Json.reads[WebSearchSource]

  private implicit lazy val webSearchSourceWrites: Writes[WebSearchSource] =
    (source: WebSearchSource) => {
      Json.obj(
        "url" -> source.url,
        "type" -> source.`type`
      )
    }

  implicit lazy val webSearchSourceFormat: Format[WebSearchSource] =
    Format(webSearchSourceReads, webSearchSourceWrites)

  // Web search action formats
  private implicit lazy val webSearchActionSearchFormat: OFormat[WebSearchAction.Search] =
    (
      (__ \ "query").formatNullable[String] and
        (__ \ "sources").formatWithDefault[Seq[WebSearchSource]](Seq.empty[WebSearchSource])
    )(
      WebSearchAction.Search.apply _,
      (x: WebSearchAction.Search) => (x.query, x.sources)
    )
  private implicit lazy val webSearchActionOpenPageFormat: OFormat[WebSearchAction.OpenPage] =
    Json.format[WebSearchAction.OpenPage]
  private implicit lazy val webSearchActionFindFormat: OFormat[WebSearchAction.Find] =
    Json.format[WebSearchAction.Find]

  implicit lazy val webSearchActionFormat: Format[WebSearchAction] =
    new Format[WebSearchAction] {
      def reads(json: JsValue): JsResult[WebSearchAction] = {
        (json \ "type").validate[String].flatMap {
          case "search"    => webSearchActionSearchFormat.reads(json)
          case "open_page" => webSearchActionOpenPageFormat.reads(json)
          case "find"      => webSearchActionFindFormat.reads(json)
          case other       => JsError(s"Unsupported web search action type: $other")
        }
      }

      def writes(action: WebSearchAction): JsValue = {
        val jsObject = action match {
          case t: WebSearchAction.Search   => webSearchActionSearchFormat.writes(t)
          case t: WebSearchAction.OpenPage => webSearchActionOpenPageFormat.writes(t)
          case t: WebSearchAction.Find     => webSearchActionFindFormat.writes(t)
        }

        jsObject ++ Json.obj("type" -> action.`type`)
      }
    }

  implicit lazy val webSearchToolCallFormat: OFormat[WebSearchToolCall] =
    (
      (__ \ "action").format[WebSearchAction] and
        (__ \ "id").format[String] and
        (__ \ "status").format[ModelStatus]
    )(
      WebSearchToolCall.apply _,
      (x: WebSearchToolCall) => (x.action, x.id, x.status)
    )
  implicit lazy val computerToolCallFormat: OFormat[ComputerToolCall] =
    (
      (__ \ "action").format[ComputerToolAction] and
        (__ \ "call_id").format[String] and
        (__ \ "id").format[String] and
        (__ \ "pending_safety_checks")
          .formatWithDefault[Seq[PendingSafetyCheck]](Seq.empty[PendingSafetyCheck]) and
        (__ \ "status").format[ModelStatus]
    )(
      ComputerToolCall.apply _,
      (x: ComputerToolCall) => (x.action, x.callId, x.id, x.pendingSafetyChecks, x.status)
    )

  implicit lazy val fileSearchToolCallFormat: OFormat[FileSearchToolCall] =
    (
      (__ \ "id").format[String] and
        (__ \ "queries").formatWithDefault[Seq[String]](Seq.empty[String]) and
        (__ \ "status").format[ModelStatus] and
        (__ \ "results").formatWithDefault[Seq[FileSearchResult]](Seq.empty[FileSearchResult])
    )(
      FileSearchToolCall.apply _,
      (x: FileSearchToolCall) => (x.id, x.queries, x.status, x.results)
    )

  implicit lazy val imageGenerationToolCallFormat: OFormat[ImageGenerationToolCall] =
    Json.format[ImageGenerationToolCall]

  implicit lazy val codeInterpreterToolCallFormat: OFormat[CodeInterpreterToolCall] =
    Json.format[CodeInterpreterToolCall]

  // Local shell action format - custom writes to include type field
  private implicit lazy val localShellActionReads: Reads[LocalShellAction] =
    Json.reads[LocalShellAction]

  private implicit lazy val localShellActionWrites: Writes[LocalShellAction] =
    (action: LocalShellAction) => {
      val baseFields = Seq(
        "command" -> Json.toJson(action.command),
        "env" -> Json.toJson(action.env),
        "type" -> JsString(action.`type`)
      )
      val optionalFields = Seq(
        action.timeoutMs.map("timeout_ms" -> Json.toJson(_)),
        action.user.map("user" -> Json.toJson(_)),
        action.workingDirectory.map("working_directory" -> Json.toJson(_))
      ).flatten

      JsObject(baseFields ++ optionalFields)
    }

  implicit lazy val localShellActionFormat: Format[LocalShellAction] =
    Format(localShellActionReads, localShellActionWrites)

  implicit lazy val localShellToolCallFormat: OFormat[LocalShellToolCall] =
    Json.format[LocalShellToolCall]

  implicit lazy val mcpToolCallFormat: OFormat[MCPToolCall] =
    Json.format[MCPToolCall]

  implicit lazy val customToolCallFormat: OFormat[CustomToolCall] =
    Json.format[CustomToolCall]

  implicit lazy val toolCallFormat: Format[ToolCall] = new Format[ToolCall] {
    def reads(json: JsValue): JsResult[ToolCall] = {
      (json \ "type").validate[String].flatMap {
        case "function_call"    => functionToolCallFormat.reads(json)
        case "web_search_call"  => webSearchToolCallFormat.reads(json)
        case "computer_call"    => computerToolCallFormat.reads(json)
        case "file_search_call" => fileSearchToolCallFormat.reads(json)
        case other              => JsError(s"Unsupported tool call type: $other")
      }
    }

    def writes(toolCall: ToolCall): JsValue = {
      val jsObject = toolCall match {
        case x: FunctionToolCall   => functionToolCallFormat.writes(x)
        case x: WebSearchToolCall  => webSearchToolCallFormat.writes(x)
        case x: ComputerToolCall   => computerToolCallFormat.writes(x)
        case x: FileSearchToolCall => fileSearchToolCallFormat.writes(x)
      }

      jsObject ++ Json.obj("type" -> toolCall.`type`)
    }
  }

  //////////////////
  // Tool Outputs //
  //////////////////

  // FunctionToolOutput format (polymorphic: string or array of InputMessageContent)
  implicit lazy val functionToolOutputFormat: Format[FunctionToolOutput] =
    new Format[FunctionToolOutput] {
      def reads(json: JsValue): JsResult[FunctionToolOutput] = {
        json match {
          case JsString(value) =>
            JsSuccess(FunctionToolOutput.StringOutput(value))
          case arr: JsArray =>
            arr
              .validate[Seq[InputMessageContent]](
                Reads.seq(
                  io.cequence.openaiscala.domain.responsesapi.JsonFormats.inputMessageContentFormat
                )
              )
              .map(content => FunctionToolOutput.ContentOutput(content))
          case _ =>
            JsError("Expected string or array for FunctionToolOutput")
        }
      }

      def writes(output: FunctionToolOutput): JsValue = output match {
        case FunctionToolOutput.StringOutput(value) =>
          JsString(value)
        case FunctionToolOutput.ContentOutput(content) =>
          Json.toJson(content)(
            Writes.seq(
              io.cequence.openaiscala.domain.responsesapi.JsonFormats.inputMessageContentFormat
            )
          )
      }
    }

  implicit lazy val functionToolCallOutputFormat: OFormat[FunctionToolCallOutput] =
    Json.format[FunctionToolCallOutput]

  // Computer tool call output related formats
  implicit lazy val acknowledgedSafetyCheckFormat: Format[AcknowledgedSafetyCheck] =
    Json.format[AcknowledgedSafetyCheck]

  private implicit lazy val computerScreenshotReads: Reads[ComputerScreenshot] =
    Json.reads[ComputerScreenshot]

  private implicit lazy val computerScreenshotWrites: Writes[ComputerScreenshot] =
    new Writes[ComputerScreenshot] {

      def writes(screenshot: ComputerScreenshot): JsValue = {
        Json.obj(
          "file_id" -> screenshot.fileId,
          "image_url" -> screenshot.imageUrl,
          "type" -> screenshot.`type`
        )
      }
    }

  implicit lazy val computerScreenshotFormat: Format[ComputerScreenshot] =
    Format(computerScreenshotReads, computerScreenshotWrites)

  implicit lazy val computerToolCallOutputFormat: OFormat[ComputerToolCallOutput] =
    (
      (__ \ "call_id").format[String] and
        (__ \ "output").format[ComputerScreenshot] and
        (__ \ "acknowledged_safety_checks").formatWithDefault[Seq[AcknowledgedSafetyCheck]](
          Seq.empty[AcknowledgedSafetyCheck]
        ) and
        (__ \ "id").formatNullable[String] and
        (__ \ "status").formatNullable[ModelStatus]
    )(
      ComputerToolCallOutput.apply _,
      (x: ComputerToolCallOutput) =>
        (x.callId, x.output, x.acknowledgedSafetyChecks, x.id, x.status)
    )

  implicit lazy val codeInterpreterOutputLogsFormat: OFormat[CodeInterpreterOutputLogs] =
    Json.format[CodeInterpreterOutputLogs]

  implicit lazy val codeInterpreterOutputImageFormat: OFormat[CodeInterpreterOutputImage] =
    Json.format[CodeInterpreterOutputImage]

  implicit lazy val codeInterpreterOutputFormat: Format[CodeInterpreterOutput] =
    new Format[CodeInterpreterOutput] {
      def reads(json: JsValue): JsResult[CodeInterpreterOutput] = {
        (json \ "type").validate[String].flatMap {
          case "logs"  => codeInterpreterOutputLogsFormat.reads(json)
          case "image" => codeInterpreterOutputImageFormat.reads(json)
          case other   => JsError(s"Unsupported code interpreter output type: $other")
        }
      }

      def writes(output: CodeInterpreterOutput): JsValue = {
        val jsObject: JsObject = output match {
          case logs: CodeInterpreterOutputLogs => codeInterpreterOutputLogsFormat.writes(logs)
          case image: CodeInterpreterOutputImage =>
            codeInterpreterOutputImageFormat.writes(image)
        }

        jsObject ++ Json.obj("type" -> output.`type`)
      }
    }

  implicit lazy val localShellToolCallOutputFormat: OFormat[LocalShellToolCallOutput] =
    Json.format[LocalShellToolCallOutput]

  implicit lazy val customToolCallOutputFormat: OFormat[CustomToolCallOutput] =
    Json.format[CustomToolCallOutput]

  // MCP tool format - custom writes to include type field
  private implicit lazy val mcpToolReads: Reads[MCPToolRef] =
    Json.reads[MCPToolRef]

  private implicit lazy val mcpToolWrites: Writes[MCPToolRef] =
    (tool: MCPToolRef) => {
      val baseFields = Seq(
        "input_schema" -> Json.toJson(tool.inputSchema),
        "name" -> Json.toJson(tool.name),
        "annotations" -> Json.toJson(tool.annotations),
        "type" -> JsString(tool.`type`)
      )
      val optionalFields = Seq(
        tool.description.map("description" -> Json.toJson(_))
      ).flatten

      JsObject(baseFields ++ optionalFields)
    }

  implicit lazy val mcpToolFormat: Format[MCPToolRef] =
    Format(mcpToolReads, mcpToolWrites)

  implicit lazy val mcpListToolsFormat: OFormat[MCPListTools] =
    Json.format[MCPListTools]

  implicit lazy val mcpApprovalRequestFormat: OFormat[MCPApprovalRequest] =
    Json.format[MCPApprovalRequest]

  implicit lazy val mcpApprovalResponseFormat: OFormat[MCPApprovalResponse] =
    Json.format[MCPApprovalResponse]

  // MCP tool error content format - custom writes to include type field
  private implicit lazy val mcpToolErrorContentReads: Reads[MCPToolErrorContent] =
    Json.reads[MCPToolErrorContent]

  private implicit lazy val mcpToolErrorContentWrites: Writes[MCPToolErrorContent] =
    (content: MCPToolErrorContent) => {
      Json.obj(
        "text" -> content.text,
        "annotations" -> content.annotations,
        "meta" -> content.meta,
        "type" -> content.`type`
      )
    }

  implicit lazy val mcpToolErrorContentFormat: Format[MCPToolErrorContent] =
    Format(mcpToolErrorContentReads, mcpToolErrorContentWrites)

  implicit lazy val mcpToolErrorFormat: OFormat[MCPToolError] =
    Json.format[MCPToolError]
}
