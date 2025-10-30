package io.cequence.openaiscala.domain.responsesapi

import java.{util => ju}
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.JsonUtil.{enumFormat, snakeEnumFormat}
import io.cequence.openaiscala.domain.responsesapi.ModelStatus
import io.cequence.openaiscala.JsonFormats.{jsonSchemaFormat, reasoningEffortFormat}
import io.cequence.openaiscala.domain.responsesapi.{TruncationStrategy, ResponseFormat}
import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.domain.responsesapi.tools._
import io.cequence.openaiscala.domain.responsesapi.InputMessageContent
import io.cequence.openaiscala.domain.responsesapi.OutputMessageContent
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats.{
  toolCallFormat,
  computerToolCallOutputFormat,
  functionToolCallOutputFormat,
  toolChoiceFormat,
  toolFormat
}
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats.{
  fileSearchToolCallFormat,
  webSearchToolCallFormat,
  computerToolCallFormat,
  imageGenerationToolCallFormat,
  codeInterpreterToolCallFormat,
  localShellToolCallFormat,
  localShellToolCallOutputFormat,
  mcpListToolsFormat,
  mcpToolCallFormat,
  mcpApprovalRequestFormat,
  mcpApprovalResponseFormat,
  customToolCallOutputFormat,
  customToolCallFormat
}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import io.cequence.openaiscala.JsonFormats.chatRoleFormat
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats.functionToolCallFormat
import io.cequence.openaiscala.domain.responsesapi.tools.mcp._

object JsonFormats {

  // general/initial config
  private implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  private val JsonWithDefaultValues = Json.using[Json.WithDefaultValues]

  private implicit lazy val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] = JsonUtil.StringAnyMapFormat

  // model status
  implicit lazy val modelStatusFormat: Format[ModelStatus] =
    snakeEnumFormat[ModelStatus](ModelStatus.values: _*)

  // truncation strategy
  implicit lazy val truncationStrategyFormat: Format[TruncationStrategy] =
    enumFormat[TruncationStrategy](TruncationStrategy.values: _*)

  // response format
  private implicit lazy val responseFormatJsonSchemaSpecFormat
    : OFormat[ResponseFormat.JsonSchemaSpec] =
    Json.format[ResponseFormat.JsonSchemaSpec]

  implicit lazy val responseFormatFormat: Format[ResponseFormat] = new Format[ResponseFormat] {
    override def reads(json: JsValue): JsResult[ResponseFormat] = {
      (json \ "type").as[String] match {
        case "text"        => JsSuccess(ResponseFormat.Text)
        case "json_object" => JsSuccess(ResponseFormat.JsonObject)
        case "json_schema" => responseFormatJsonSchemaSpecFormat.reads(json)
        case other         => JsError(s"Unknown ResponseFormat type: $other")
      }
    }

    override def writes(o: ResponseFormat): JsValue = {
      val baseJson = o match {
        case ResponseFormat.Text              => Json.obj()
        case ResponseFormat.JsonObject        => Json.obj()
        case j: ResponseFormat.JsonSchemaSpec => responseFormatJsonSchemaSpecFormat.writes(j)
      }

      // Convert to JsObject and add type
      baseJson.as[JsObject] + ("type" -> JsString(o.`type`))
    }
  }

  // text response config
  implicit lazy val textResponseConfigFormat: Format[TextResponseConfig] =
    Json.format[TextResponseConfig]

  // reasoning config
  implicit lazy val reasoningConfigFormat: Format[ReasoningConfig] =
    Json.format[ReasoningConfig]

  // summary text - custom format to include type field
  private implicit lazy val summaryTextReads: Reads[SummaryText] =
    Json.reads[SummaryText]

  private implicit lazy val summaryTextWrites: Writes[SummaryText] =
    (summaryText: SummaryText) => {
      Json.obj(
        "text" -> summaryText.text,
        "type" -> summaryText.`type`
      )
    }

  implicit lazy val summaryTextFormat: Format[SummaryText] =
    Format(summaryTextReads, summaryTextWrites)

  // reasoning text - custom format to include type field
  private implicit lazy val reasoningTextReads: Reads[ReasoningText] =
    Json.reads[ReasoningText]

  private implicit lazy val reasoningTextWrites: Writes[ReasoningText] =
    (reasoningText: ReasoningText) => {
      Json.obj(
        "text" -> reasoningText.text,
        "type" -> reasoningText.`type`
      )
    }

  implicit lazy val reasoningTextFormat: Format[ReasoningText] =
    Format(reasoningTextReads, reasoningTextWrites)

  // reasoning
  implicit lazy val reasoningFormat: OFormat[Reasoning] =
    (
      (__ \ "id").format[String] and
        (__ \ "summary").formatWithDefault[Seq[SummaryText]](Seq.empty[SummaryText]) and
        (__ \ "content").formatWithDefault[Seq[ReasoningText]](Seq.empty[ReasoningText]) and
        (__ \ "encrypted_content").formatNullable[String] and
        (__ \ "status").formatNullable[ModelStatus]
    )(
      Reasoning.apply _,
      (x: Reasoning) => (x.id, x.summary, x.content, x.encryptedContent, x.status)
    )

  // item reference
  implicit lazy val itemReferenceFormat: OFormat[ItemReference] =
    Json.format[ItemReference]

  // input tokens details
  implicit lazy val inputTokensDetailsFormat: Format[InputTokensDetails] =
    Json.format[InputTokensDetails]

  // output tokens details
  implicit lazy val outputTokensDetailsFormat: Format[OutputTokensDetails] =
    Json.format[OutputTokensDetails]

  // usage info
  implicit lazy val usageInfoFormat: Format[UsageInfo] =
    Json.format[UsageInfo]

  // input message content hierarchy
  private implicit lazy val inputMessageContentTextFormat: OFormat[InputMessageContent.Text] =
    Json.format[InputMessageContent.Text]

  private implicit lazy val inputMessageContentImageFormat
    : OFormat[InputMessageContent.Image] =
    Json.format[InputMessageContent.Image]

  private implicit lazy val inputMessageContentFileFormat: OFormat[InputMessageContent.File] =
    Json.format[InputMessageContent.File]

  implicit lazy val inputMessageContentFormat: Format[InputMessageContent] =
    new Format[InputMessageContent] {
      override def reads(json: JsValue): JsResult[InputMessageContent] = {
        (json \ "type").as[String] match {
          case "input_text"  => inputMessageContentTextFormat.reads(json)
          case "input_image" => inputMessageContentImageFormat.reads(json)
          case "input_file"  => inputMessageContentFileFormat.reads(json)
          case unknown       => JsError(s"Unknown InputMessageContent type: $unknown")
        }
      }

      override def writes(content: InputMessageContent): JsValue = {
        val jsObject = content match {
          case text: InputMessageContent.Text   => inputMessageContentTextFormat.writes(text)
          case image: InputMessageContent.Image => inputMessageContentImageFormat.writes(image)
          case file: InputMessageContent.File   => inputMessageContentFileFormat.writes(file)
        }
        jsObject + ("type" -> JsString(content.`type`))
      }
    }

  // output message content hierarchy

  private implicit lazy val urlCitationFormat: OFormat[Annotation.UrlCitation] =
    Json.format[Annotation.UrlCitation]

  private implicit lazy val fileCitationFormat: OFormat[Annotation.FileCitation] =
    Json.format[Annotation.FileCitation]

  private implicit lazy val annotationReads: Reads[Annotation] = { (json: JsValue) =>
    {
      val citationType = (json \ "type").as[String]
      citationType match {
        case "url_citation"  => urlCitationFormat.reads(json)
        case "file_citation" => fileCitationFormat.reads(json)
        case unknown         => JsError(s"Unknown citation type: $unknown")
      }
    }
  }

  private implicit lazy val annotationWrites: Writes[Annotation] = {
    (annotation: Annotation) =>
      {
        val jsObject = annotation match {
          case urlCitation: Annotation.UrlCitation   => urlCitationFormat.writes(urlCitation)
          case fileCitation: Annotation.FileCitation => fileCitationFormat.writes(fileCitation)
        }

        jsObject + ("type" -> JsString(annotation.`type`))
      }
  }

  implicit lazy val annotationFormat: Format[Annotation] =
    Format(annotationReads, annotationWrites)

  private implicit lazy val outputMessageContentTextFormat
    : OFormat[OutputMessageContent.OutputText] =
    Json.format[OutputMessageContent.OutputText]

  private implicit lazy val outputMessageContentRefusalFormat
    : OFormat[OutputMessageContent.Refusal] =
    Json.format[OutputMessageContent.Refusal]

  implicit lazy val outputMessageContentFormat: Format[OutputMessageContent] =
    new Format[OutputMessageContent] {
      override def reads(json: JsValue): JsResult[OutputMessageContent] = {
        (json \ "type").as[String] match {
          case "output_text" => outputMessageContentTextFormat.reads(json)
          case "refusal"     => outputMessageContentRefusalFormat.reads(json)
          case unknown       => JsError(s"Unknown OutputMessageContent type: $unknown")
        }
      }

      override def writes(content: OutputMessageContent): JsValue = {
        val jsObject = content match {
          case text: OutputMessageContent.OutputText =>
            outputMessageContentTextFormat.writes(text)
          case refusal: OutputMessageContent.Refusal =>
            outputMessageContentRefusalFormat.writes(refusal)
        }
        jsObject + ("type" -> JsString(content.`type`))
      }
    }

  // message hierarchy

  implicit lazy val inputTextMessageFormat: OFormat[Message.InputText] =
    Json.format[Message.InputText]

  implicit lazy val inputContentMessageFormat: OFormat[Message.InputContent] =
    Json.format[Message.InputContent]

  implicit lazy val outputContentMessageFormat: OFormat[Message.OutputContent] =
    Json.format[Message.OutputContent]

  implicit lazy val messageWrites: Writes[Message] = Writes[Message] {
    case inputTextMessage: Message.InputText => inputTextMessageFormat.writes(inputTextMessage)
    case inputMessage: Message.InputContent  => inputContentMessageFormat.writes(inputMessage)
    case outputMessage: Message.OutputContent =>
      outputContentMessageFormat.writes(outputMessage)
  }

  // input hierarchy

  implicit lazy val inputWrites: Writes[Input] = Writes[Input] { (input: Input) =>
    val jsObject = input match {
      case input: Message.InputText        => inputTextMessageFormat.writes(input)
      case input: Message.InputContent     => inputContentMessageFormat.writes(input)
      case input: Message.OutputContent    => outputContentMessageFormat.writes(input)
      case input: FileSearchToolCall       => fileSearchToolCallFormat.writes(input)
      case input: ComputerToolCall         => computerToolCallFormat.writes(input)
      case input: ComputerToolCallOutput   => computerToolCallOutputFormat.writes(input)
      case input: WebSearchToolCall        => webSearchToolCallFormat.writes(input)
      case input: FunctionToolCall         => functionToolCallFormat.writes(input)
      case input: FunctionToolCallOutput   => functionToolCallOutputFormat.writes(input)
      case input: Reasoning                => reasoningFormat.writes(input)
      case input: ItemReference            => itemReferenceFormat.writes(input)
      case input: ImageGenerationToolCall  => imageGenerationToolCallFormat.writes(input)
      case input: CodeInterpreterToolCall  => codeInterpreterToolCallFormat.writes(input)
      case input: LocalShellToolCall       => localShellToolCallFormat.writes(input)
      case input: LocalShellToolCallOutput => localShellToolCallOutputFormat.writes(input)
      case input: MCPListTools             => mcpListToolsFormat.writes(input)
      case input: MCPToolCall              => mcpToolCallFormat.writes(input)
      case input: MCPApprovalRequest       => mcpApprovalRequestFormat.writes(input)
      case input: MCPApprovalResponse      => mcpApprovalResponseFormat.writes(input)
      case input: CustomToolCallOutput     => customToolCallOutputFormat.writes(input)
      case input: CustomToolCall           => customToolCallFormat.writes(input)
    }
    jsObject + ("type" -> JsString(input.`type`))
  }

  implicit lazy val inputReads: Reads[Input] = Reads[Input] { json =>
    (json \ "type").as[String] match {
      case "message" =>
        // Determine message type based on content
        (json \ "content").validate[JsValue] match {
          case JsSuccess(JsString(_), _) => inputTextMessageFormat.reads(json)
          case JsSuccess(JsArray(items), _) =>
            items.headOption.map { item =>
              (item \ "type").validate[String] match {
                case JsSuccess("output_text", _) | JsSuccess("refusal", _) =>
                  outputContentMessageFormat.reads(json)
                case JsSuccess("input_text", _) | JsSuccess("input_image", _) |
                    JsSuccess("input_file", _) =>
                  inputContentMessageFormat.reads(json)
                case _ => JsError("Unknown Input type")
              }
            }.getOrElse(JsError("Content array is empty"))

          case JsSuccess(_, _) => JsError("Content is not a string or array")
          case JsError(_)      => JsError("Missing 'content' field for Input")
        }

      case "file_search_call"        => fileSearchToolCallFormat.reads(json)
      case "computer_call"           => computerToolCallFormat.reads(json)
      case "computer_call_output"    => computerToolCallOutputFormat.reads(json)
      case "web_search_call"         => webSearchToolCallFormat.reads(json)
      case "function_call"           => functionToolCallFormat.reads(json)
      case "function_call_output"    => functionToolCallOutputFormat.reads(json)
      case "reasoning"               => reasoningFormat.reads(json)
      case "item_reference"          => itemReferenceFormat.reads(json)
      case "image_generation_call"   => imageGenerationToolCallFormat.reads(json)
      case "code_interpreter_call"   => codeInterpreterToolCallFormat.reads(json)
      case "local_shell_call"        => localShellToolCallFormat.reads(json)
      case "local_shell_call_output" => localShellToolCallOutputFormat.reads(json)
      case "mcp_list_tools"          => mcpListToolsFormat.reads(json)
      case "mcp_call"                => mcpToolCallFormat.reads(json)
      case "mcp_approval_request"    => mcpApprovalRequestFormat.reads(json)
      case "mcp_approval_response"   => mcpApprovalResponseFormat.reads(json)
      case "custom_tool_call_output" => customToolCallOutputFormat.reads(json)
      case "custom_tool_call"        => customToolCallFormat.reads(json)
      case _                         => JsError("Missing type field for Input")
    }
  }

  implicit lazy val inputFormat: Format[Input] = Format(inputReads, inputWrites)

  // inputs

  implicit lazy val inputsReads: Reads[Inputs] = Reads[Inputs] {
    case JsString(text) => JsSuccess(Inputs.Text(text))
    case JsArray(items) =>
      val inputResults = items.map(inputReads.reads)
      val errors = inputResults.collect { case JsError(e) => e }
      if (errors.nonEmpty) {
        JsError(errors.flatten)
      } else {
        val inputs = inputResults.collect { case JsSuccess(input, _) => input }
        JsSuccess(Inputs.Items(inputs.toSeq: _*))
      }
    case _ => JsError("Expected string or array for Inputs")
  }

  implicit lazy val inputsWrites: Writes[Inputs] = Writes[Inputs] {
    case inputs: Inputs.Text  => JsString(inputs.text)
    case inputs: Inputs.Items => JsArray(inputs.items.map(inputWrites.writes))
  }

  implicit lazy val inputsFormat: Format[Inputs] = Format(inputsReads, inputsWrites)

  // output

  implicit lazy val outputFormat: Format[Output] = new Format[Output] {
    override def reads(json: JsValue): JsResult[Output] = {
      (json \ "type").as[String] match {
        case "message"          => outputContentMessageFormat.reads(json)
        case "file_search_call" => fileSearchToolCallFormat.reads(json)
        case "web_search_call"  => webSearchToolCallFormat.reads(json)
        case "computer_call"    => computerToolCallFormat.reads(json)
        case "function_call"    => functionToolCallFormat.reads(json)
        case "reasoning"        => reasoningFormat.reads(json)
        case unknown            => JsError(s"Unknown Output type: $unknown")
      }
    }

    override def writes(output: Output): JsValue = {
      val jsObject = output match {
        case output: Message.OutputContent => outputContentMessageFormat.writes(output)
        case output: FileSearchToolCall    => fileSearchToolCallFormat.writes(output)
        case output: WebSearchToolCall     => webSearchToolCallFormat.writes(output)
        case output: ComputerToolCall      => computerToolCallFormat.writes(output)
        case output: FunctionToolCall      => functionToolCallFormat.writes(output)
        case output: Reasoning             => reasoningFormat.writes(output)
      }

      jsObject.asInstanceOf[JsObject] + ("type" -> JsString(output.`type`))
    }
  }

  private def writesNonEmpty(fieldName: String) = (jsObject: JsObject) => {
    val include = (jsObject \ fieldName).as[JsArray].value
    if (include.nonEmpty) jsObject else jsObject.-(fieldName)
  }

  implicit lazy val promptFormat: OFormat[Prompt] = {
    val reads: Reads[Prompt] = (
      (__ \ "id").read[String] and
        (__ \ "variables").readWithDefault[Map[String, String]](Map.empty) and
        (__ \ "version").readNullable[String]
    )(Prompt.apply _)

    val writes: OWrites[Prompt] = Json.writes[Prompt]

    OFormat(reads, writes)
  }

  implicit lazy val streamOptionsFormat: OFormat[StreamOptions] = Json.format[StreamOptions]

  // create model response
  private implicit lazy val createModelResponseSettingsAuxPart1Reads
    : Reads[CreateModelResponseSettingsAuxPart1] =
    (
      (__ \ "model").read[String] and
        (__ \ "include").readWithDefault[Seq[String]](Nil) and
        (__ \ "instructions").readNullable[String] and
        (__ \ "max_output_tokens").readNullable[Int] and
        (__ \ "metadata").readNullable[Map[String, String]] and
        (__ \ "parallel_tool_calls").readNullable[Boolean] and
        (__ \ "previous_response_id").readNullable[String] and
        (__ \ "reasoning").readNullable[ReasoningConfig] and
        (__ \ "store").readNullable[Boolean] and
        (__ \ "stream").readNullable[Boolean] and
        (__ \ "temperature").readNullable[Double] and
        (__ \ "text").readNullable[TextResponseConfig]
    )(CreateModelResponseSettingsAuxPart1.apply _)

  private implicit lazy val createModelResponseSettingsAuxPart1Writes
    : OWrites[CreateModelResponseSettingsAuxPart1] =
    (
      (__ \ "model").write[String] and
        (__ \ "include").write[Seq[String]].transform(writesNonEmpty("include")) and
        (__ \ "instructions").writeNullable[String] and
        (__ \ "max_output_tokens").writeNullable[Int] and
        (__ \ "metadata").writeNullable[Map[String, String]] and
        (__ \ "parallel_tool_calls").writeNullable[Boolean] and
        (__ \ "previous_response_id").writeNullable[String] and
        (__ \ "reasoning").writeNullable[ReasoningConfig] and
        (__ \ "store").writeNullable[Boolean] and
        (__ \ "stream").writeNullable[Boolean] and
        (__ \ "temperature").writeNullable[Double] and
        (__ \ "text").writeNullable[TextResponseConfig]
    )(
      // somehow FineTuneJob.unapply is not working in Scala3
      (x: CreateModelResponseSettingsAuxPart1) =>
        (
          x.model,
          x.include,
          x.instructions,
          x.maxOutputTokens,
          x.metadata,
          x.parallelToolCalls,
          x.previousResponseId,
          x.reasoning,
          x.store,
          x.stream,
          x.temperature,
          x.text
        )
    )

  private implicit lazy val createModelResponseSettingsAuxPart2Reads
    : Reads[CreateModelResponseSettingsAuxPart2] =
    (
      (__ \ "tool_choice").readNullable[ToolChoice] and
        (__ \ "tools").readWithDefault[Seq[Tool]](Nil) and
        (__ \ "top_p").readNullable[Double] and
        (__ \ "truncation").readNullable[TruncationStrategy] and
        (__ \ "user").readNullable[String] and
        (__ \ "prompt").readNullable[Prompt] and
        (__ \ "prompt_cache_key").readNullable[String] and
        (__ \ "background").readNullable[Boolean] and
        (__ \ "max_tool_calls").readNullable[Int] and
        (__ \ "safety_identifier").readNullable[String] and
        (__ \ "service_tier").readNullable[String] and
        (__ \ "stream_options").readNullable[StreamOptions] and
        (__ \ "top_logprobs").readNullable[Int]
    )(CreateModelResponseSettingsAuxPart2.apply _)

  private implicit lazy val createModelResponseSettingsAuxPart2Writes
    : OWrites[CreateModelResponseSettingsAuxPart2] =
    (
      (__ \ "tool_choice").writeNullable[ToolChoice] and
        (__ \ "tools").write[Seq[Tool]].transform(writesNonEmpty("tools")) and
        (__ \ "top_p").writeNullable[Double] and
        (__ \ "truncation").writeNullable[TruncationStrategy] and
        (__ \ "user").writeNullable[String] and
        (__ \ "prompt").writeNullable[Prompt] and
        (__ \ "prompt_cache_key").writeNullable[String] and
        (__ \ "background").writeNullable[Boolean] and
        (__ \ "max_tool_calls").writeNullable[Int] and
        (__ \ "safety_identifier").writeNullable[String] and
        (__ \ "service_tier").writeNullable[String] and
        (__ \ "stream_options").writeNullable[StreamOptions] and
        (__ \ "top_logprobs").writeNullable[Int]
    )(
      // somehow FineTuneJob.unapply is not working in Scala3
      (x: CreateModelResponseSettingsAuxPart2) =>
        (
          x.toolChoice,
          x.tools,
          x.topP,
          x.truncation,
          x.user,
          x.prompt,
          x.promptCacheKey,
          x.background,
          x.maxToolCalls,
          x.safetyIdentifier,
          x.serviceTier,
          x.streamOptions,
          x.topLogprobs
        )
    )

  // Compose Reads and Writes for CreateModelResponseSettings using the AuxPart1 and AuxPart2
  implicit lazy val createModelResponseSettingsReads: Reads[CreateModelResponseSettings] =
    for {
      part1 <- createModelResponseSettingsAuxPart1Reads
      part2 <- createModelResponseSettingsAuxPart2Reads
    } yield CreateModelResponseSettings(
      model = part1.model,
      include = part1.include,
      instructions = part1.instructions,
      maxOutputTokens = part1.maxOutputTokens,
      metadata = part1.metadata,
      parallelToolCalls = part1.parallelToolCalls,
      previousResponseId = part1.previousResponseId,
      reasoning = part1.reasoning,
      store = part1.store,
      stream = part1.stream,
      temperature = part1.temperature,
      text = part1.text,
      toolChoice = part2.toolChoice,
      tools = part2.tools,
      topP = part2.topP,
      truncation = part2.truncation,
      user = part2.user,
      prompt = part2.prompt,
      promptCacheKey = part2.promptCacheKey,
      background = part2.background,
      maxToolCalls = part2.maxToolCalls,
      safetyIdentifier = part2.safetyIdentifier,
      serviceTier = part2.serviceTier,
      streamOptions = part2.streamOptions,
      topLogprobs = part2.topLogprobs
    )

  implicit lazy val createModelResponseSettingsWrites: OWrites[CreateModelResponseSettings] =
    OWrites[CreateModelResponseSettings] { x =>
      val part1Json = createModelResponseSettingsAuxPart1Writes.writes(
        CreateModelResponseSettings.toAuxPart1(x)
      )
      val part2Json = createModelResponseSettingsAuxPart2Writes.writes(
        CreateModelResponseSettings.toAuxPart2(x)
      )
      part1Json ++ part2Json
    }

  implicit lazy val createModelResponseSettingsFormat: OFormat[CreateModelResponseSettings] =
    OFormat(createModelResponseSettingsReads, createModelResponseSettingsWrites)

  implicit lazy val getInputTokensCountSettingsFormat: OFormat[GetInputTokensCountSettings] =
    Json.format[GetInputTokensCountSettings]

  // response
  implicit lazy val conversationFormat: Format[Conversation] = Json.format[Conversation]

  implicit lazy val responseErrorFormat: Format[ResponseError] = Json.format[ResponseError]

  implicit lazy val incompleteDetailsFormat: Format[IncompleteDetails] =
    Json.format[IncompleteDetails]

  // needed because we exceed 22 parameters limit in case class
  implicit lazy val responseFormat: Format[Response] = new Format[Response] {
    def reads(json: JsValue): JsResult[Response] = {
      for {
        background <- (json \ "background").validateOpt[Boolean]
        conversation <- (json \ "conversation").validateOpt[Conversation]
        createdAt <- (json \ "created_at").validate[ju.Date]
        error <- (json \ "error").validateOpt[ResponseError]
        id <- (json \ "id").validate[String]
        incompleteDetails <- (json \ "incomplete_details").validateOpt[IncompleteDetails]
        instructions <- (json \ "instructions").validateOpt[Inputs]
        maxOutputTokens <- (json \ "max_output_tokens").validateOpt[Int]
        maxToolCalls <- (json \ "max_tool_calls").validateOpt[Int]
        metadata <- (json \ "metadata").validateOpt[Map[String, String]]
        model <- (json \ "model").validate[String]
        objectType <- (json \ "object").validateOpt[String].map(_.getOrElse("response"))
        output <- (json \ "output").validateOpt[Seq[Output]].map(_.getOrElse(Nil))
        parallelToolCalls <- (json \ "parallel_tool_calls").validate[Boolean]
        previousResponseId <- (json \ "previous_response_id").validateOpt[String]
        prompt <- (json \ "prompt").validateOpt[Prompt]
        promptCacheKey <- (json \ "prompt_cache_key").validateOpt[String]
        reasoning <- (json \ "reasoning").validateOpt[ReasoningConfig]
        safetyIdentifier <- (json \ "safety_identifier").validateOpt[String]
        serviceTier <- (json \ "service_tier").validateOpt[String]
        status <- (json \ "status").validate[ModelStatus]
        temperature <- (json \ "temperature").validateOpt[Double]
        text <- (json \ "text").validate[TextResponseConfig]
        toolChoice <- (json \ "tool_choice").validateOpt[ToolChoice]
        tools <- (json \ "tools").validateOpt[Seq[Tool]].map(_.getOrElse(Nil))
        topLogprobs <- (json \ "top_logprobs").validateOpt[Int]
        topP <- (json \ "top_p").validateOpt[Double]
        truncation <- (json \ "truncation").validateOpt[TruncationStrategy]
        usage <- (json \ "usage").validateOpt[UsageInfo]
        user <- (json \ "user").validateOpt[String]
      } yield Response(
        background,
        conversation,
        createdAt,
        error,
        id,
        incompleteDetails,
        instructions,
        maxOutputTokens,
        maxToolCalls,
        metadata,
        model,
        objectType,
        output,
        parallelToolCalls,
        previousResponseId,
        prompt,
        promptCacheKey,
        reasoning,
        safetyIdentifier,
        serviceTier,
        status,
        temperature,
        text,
        toolChoice,
        tools,
        topLogprobs,
        topP,
        truncation,
        usage,
        user
      )
    }

    def writes(r: Response): JsValue = {
      Json.obj(
        "background" -> r.background,
        "conversation" -> r.conversation,
        "created_at" -> r.createdAt,
        "error" -> r.error,
        "id" -> r.id,
        "incomplete_details" -> r.incompleteDetails,
        "instructions" -> r.instructions,
        "max_output_tokens" -> r.maxOutputTokens,
        "max_tool_calls" -> r.maxToolCalls,
        "metadata" -> r.metadata,
        "model" -> r.model,
        "object" -> r.`object`,
        "output" -> r.output,
        "parallel_tool_calls" -> r.parallelToolCalls,
        "previous_response_id" -> r.previousResponseId,
        "prompt" -> r.prompt,
        "prompt_cache_key" -> r.promptCacheKey,
        "reasoning" -> r.reasoning,
        "safety_identifier" -> r.safetyIdentifier,
        "service_tier" -> r.serviceTier,
        "status" -> r.status,
        "temperature" -> r.temperature,
        "text" -> r.text,
        "tool_choice" -> r.toolChoice,
        "tools" -> r.tools,
        "top_logprobs" -> r.topLogprobs,
        "top_p" -> r.topP,
        "truncation" -> r.truncation,
        "usage" -> r.usage,
        "user" -> r.user
      )
    }
  }

  implicit lazy val responsesDeleteResponseFormat: Format[DeleteResponse] =
    Json.format[DeleteResponse]

  implicit lazy val inputTokensCountFormat: Format[InputTokensCount] =
    Json.format[InputTokensCount]

  implicit val inputItemsResponseFormat: Format[InputItemsResponse] =
    Json.format[InputItemsResponse]
}
