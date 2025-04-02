package io.cequence.openaiscala.domain.responsesapi.tools

import java.{util => ju}
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.JsonUtil.{enumFormat, snakeEnumFormat}
import io.cequence.openaiscala.domain.responsesapi.ModelStatus
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.domain.responsesapi.tools._
import io.cequence.openaiscala.domain.responsesapi.JsonFormats.modelStatusFormat
import play.api.libs.functional.syntax._
import play.api.libs.json._
import io.cequence.openaiscala.domain.responsesapi.tools.ComputerToolAction._

object JsonFormats {

  // general/initial config
  private implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  private implicit lazy val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  private implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] =
    JsonUtil.StringAnyMapFormat

  //////////////////
  // Tool Choices //
  //////////////////

  implicit lazy val toolChoiceModeFormat: Format[ToolChoice.Mode] = {
    enumFormat[ToolChoice.Mode](ToolChoice.Mode.values: _*)
  }

  private implicit lazy val toolChoiceHostedToolFormat: Format[ToolChoice.HostedTool] =
    Json.format[ToolChoice.HostedTool]

  private implicit lazy val toolChoiceFunctionToolFormat: OFormat[ToolChoice.FunctionTool] =
    Json.format[ToolChoice.FunctionTool]

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
            case "function" => toolChoiceFunctionToolFormat.reads(obj)
            case hostedType @ ("file_search" | "web_search_preview" |
                "computer_use_preview") =>
              JsSuccess(ToolChoice.HostedTool(hostedType))

            case other =>
              JsError(s"Unsupported ToolChoice type: $other")
          }
        case _ => JsError("Expected string or object for ToolChoice")
      }
    }

    def writes(toolChoice: ToolChoice): JsValue = toolChoice match {
      case mode: ToolChoice.Mode =>
        JsString(mode.toString)

      // here type is a field hence handled by writes
      case x: ToolChoice.HostedTool =>
        Json.toJson(x)(toolChoiceHostedToolFormat)

      case ft: ToolChoice.FunctionTool =>
        Json.toJsObject(ft)(toolChoiceFunctionToolFormat) ++
          Json.obj("type" -> "function")
    }
  }

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
  private implicit lazy val functionToolFormat: OFormat[FunctionTool] =
    Json.format[FunctionTool]

  // web search tool
  implicit lazy val webSearchTypeFormat: Format[WebSearchType] =
    enumFormat[WebSearchType](WebSearchType.values: _*)

  implicit lazy val webSearchUserLocationFormat: Format[WebSearchUserLocation] =
    Json.format[WebSearchUserLocation]

  private implicit lazy val webSearchToolFormat: OFormat[WebSearchTool] =
    Json.format[WebSearchTool]

  // computer use tool
  private implicit lazy val computerUseToolFormat: OFormat[ComputerUseTool] =
    Json.format[ComputerUseTool]

  implicit lazy val toolFormat: Format[Tool] = new Format[Tool] {
    def reads(json: JsValue): JsResult[Tool] = {
      (json \ "type").validate[String].flatMap {
        case "file_search"                   => fileSearchToolFormat.reads(json)
        case "function"                      => functionToolFormat.reads(json)
        case t if t.startsWith("web_search") => webSearchToolFormat.reads(json)
        case "computer_use_preview"          => computerUseToolFormat.reads(json)

        case other => JsError(s"Unsupported tool type: $other")
      }
    }

    def writes(tool: Tool): JsValue = {
      val jsObject: JsObject = tool match {
        case t: FunctionTool    => functionToolFormat.writes(t)
        case t: FileSearchTool  => fileSearchToolFormat.writes(t)
        case t: WebSearchTool   => webSearchToolFormat.writes(t)
        case t: ComputerUseTool => computerUseToolFormat.writes(t)
      }

      jsObject ++ Json.obj("type" -> tool.typeString)
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
  implicit lazy val webSearchToolCallFormat: OFormat[WebSearchToolCall] =
    Json.format[WebSearchToolCall]
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
}
