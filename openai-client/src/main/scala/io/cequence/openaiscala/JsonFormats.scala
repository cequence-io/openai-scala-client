package io.cequence.openaiscala

import io.cequence.openaiscala.JsonUtil.{JsonOps, enumFormat}
import io.cequence.openaiscala.domain.{ThreadMessageFile, _}

import java.{util => ju}
import io.cequence.openaiscala.domain.response._
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, _}
import Json.toJson

object JsonFormats {
  private implicit val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  implicit val permissionFormat: Format[Permission] = Json.format[Permission]
  implicit val modelSpecFormat: Format[ModelInfo] = {
    val reads: Reads[ModelInfo] = (
      (__ \ "id").read[String] and
        (__ \ "created").read[ju.Date] and
        (__ \ "owned_by").read[String] and
        (__ \ "root").readNullable[String] and
        (__ \ "parent").readNullable[String] and
        (__ \ "permission").read[Seq[Permission]].orElse(Reads.pure(Nil))
    )(ModelInfo.apply _)

    val writes: Writes[ModelInfo] = Json.writes[ModelInfo]
    Format(reads, writes)
  }

  implicit val usageInfoFormat: Format[UsageInfo] = Json.format[UsageInfo]

  private implicit val stringDoubleMapFormat: Format[Map[String, Double]] =
    JsonUtil.StringDoubleMapFormat
  private implicit val stringStringMapFormat: Format[Map[String, String]] =
    JsonUtil.StringStringMapFormat

  implicit val logprobsInfoFormat: Format[LogprobsInfo] =
    Json.format[LogprobsInfo]
  implicit val textCompletionChoiceInfoFormat: Format[TextCompletionChoiceInfo] =
    Json.format[TextCompletionChoiceInfo]
  implicit val textCompletionFormat: Format[TextCompletionResponse] =
    Json.format[TextCompletionResponse]

  implicit val chatRoleFormat: Format[ChatRole] = enumFormat[ChatRole](
    ChatRole.User,
    ChatRole.System,
    ChatRole.Assistant,
    ChatRole.Function,
    ChatRole.Tool
  )

  implicit val functionCallSpecFormat: Format[FunctionCallSpec] =
    Json.format[FunctionCallSpec]

  implicit val systemMessageFormat: Format[SystemMessage] = Json.format[SystemMessage]
  implicit val userMessageFormat: Format[UserMessage] = Json.format[UserMessage]
  implicit val toolMessageFormat: Format[ToolMessage] = Json.format[ToolMessage]
  implicit val assistantMessageFormat: Format[AssistantMessage] = Json.format[AssistantMessage]
  implicit val assistantToolMessageReads: Reads[AssistantToolMessage] = (
    (__ \ "content").readNullable[String] and
      (__ \ "name").readNullable[String] and
      (__ \ "tool_calls").read[JsArray]
  ) {
    (
      content,
      name,
      tool_calls
    ) =>
      val idToolCalls = tool_calls.value.toSeq.map { toolCall =>
        val callId = (toolCall \ "id").as[String]
        val callType = (toolCall \ "type").as[String]
        val call: ToolCallSpec = callType match {
          case "function" => (toolCall \ "function").as[FunctionCallSpec]
          case _          => throw new Exception(s"Unknown tool call type: $callType")
        }
        (callId, call)
      }
      AssistantToolMessage(content, name, idToolCalls)
  }
  implicit val assistantFunMessageFormat: Format[AssistantFunMessage] =
    Json.format[AssistantFunMessage]

  implicit val funMessageFormat: Format[FunMessage] = Json.format[FunMessage]

  implicit val messageSpecFormat: Format[MessageSpec] = Json.format[MessageSpec]

  implicit val functionSpecFormat: Format[FunctionSpec] = {
    // use just here for FunctionSpec
    implicit val stringAnyMapFormat: Format[Map[String, Any]] = JsonUtil.StringAnyMapFormat
    Json.format[FunctionSpec]
  }

  val assistantsFunctionSpecFormat: Format[FunctionSpec] = {
    implicit val stringAnyMapFormat: Format[Map[String, Any]] = JsonUtil.StringAnyMapFormat

    val assistantsFunctionSpecWrites: Writes[FunctionSpec] = new Writes[FunctionSpec] {
      def writes(fs: FunctionSpec): JsValue = Json.obj(
        "type" -> "function",
        "function" -> Json.obj(
          "name" -> fs.name,
          "description" -> fs.description,
          "parameters" -> fs.parameters
        )
      )
    }

    val assistantsFunctionSpecReads: Reads[FunctionSpec] = (
      (JsPath \ "function" \ "name").read[String] and
        (JsPath \ "function" \ "description").readNullable[String] and
        (JsPath \ "function" \ "parameters").read[Map[String, Any]]
    )(FunctionSpec.apply _)

    Format(assistantsFunctionSpecReads, assistantsFunctionSpecWrites)
  }

  implicit val assistantToolFormat: Format[AssistantTool] = {
    val typeDiscriminatorKey = "type"

    Format[AssistantTool](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap {
          case "code_interpreter" => JsSuccess(CodeInterpreterSpec)
          case "retrieval"        => JsSuccess(RetrievalSpec)
          case "function"         => json.validate[FunctionSpec](assistantsFunctionSpecFormat)
          case _                  => JsError("Unknown type")
        }
      },
      { (tool: AssistantTool) =>
        val commonJson = Json.obj {
          val discriminatorValue = tool match {
            case CodeInterpreterSpec   => "code_interpreter"
            case RetrievalSpec         => "retrieval"
            case FunctionSpec(_, _, _) => "function"
          }
          typeDiscriminatorKey -> discriminatorValue
        }
        tool match {
          case CodeInterpreterSpec => commonJson
          case RetrievalSpec       => commonJson
          case ft: FunctionSpec =>
            commonJson ++ Json.toJson(ft)(assistantsFunctionSpecFormat).as[JsObject]
        }
      }
    )
  }

  implicit val contentWrites: Writes[Content] = Writes[Content] {
    _ match {
      case c: TextContent =>
        Json.obj("type" -> "text", "text" -> c.text)

      case c: ImageURLContent =>
        Json.obj("type" -> "image_url", "image_url" -> Json.obj("url" -> c.url))
    }
  }

  implicit val messageWrites: Writes[BaseMessage] = Writes { (message: BaseMessage) =>
    def optionalJsObject(
      fieldName: String,
      value: Option[JsValue]
    ) =
      value.map(x => Json.obj(fieldName -> x)).getOrElse(Json.obj())

    val role = Json.obj("role" -> toJson(message.role))
    val name = optionalJsObject("name", message.nameOpt.map(JsString(_)))

    val json = message match {
      case m: SystemMessage => toJson(m)

      case m: UserMessage => toJson(m)

      case m: UserSeqMessage => Json.obj("content" -> toJson(m.content))

      case m: AssistantMessage => toJson(m)

      case m: AssistantToolMessage =>
        val calls = m.tool_calls.map { case (callId, call) =>
          call match {
            case c: FunctionCallSpec =>
              Json.obj("id" -> callId, "type" -> "function", "function" -> toJson(c))
          }
        }

        optionalJsObject(
          "tool_calls",
          if (calls.nonEmpty) Some(JsArray(calls)) else None
        ) ++ optionalJsObject(
          "content",
          m.content.map(JsString(_))
        )

      case m: AssistantFunMessage => toJson(m)

      case m: ToolMessage => toJson(m)

      case m: FunMessage => toJson(m)

      case m: MessageSpec => toJson(m)
    }

    json.as[JsObject] ++ role ++ name
  }

  implicit val toolWrites: Writes[ToolSpec] = Writes[ToolSpec] {
    _ match {
      case x: FunctionSpec =>
        Json.obj("type" -> "function", "function" -> Json.toJson(x))
    }
  }

  implicit val topLogprobInfoormat: Format[TopLogprobInfo] =
    Json.format[TopLogprobInfo]
  implicit val logprobInfoFormat: Format[LogprobInfo] =
    Json.format[LogprobInfo]
  implicit val logprobsFormat: Format[Logprobs] =
    Json.format[Logprobs]

  implicit val chatCompletionChoiceInfoFormat: Format[ChatCompletionChoiceInfo] =
    Json.format[ChatCompletionChoiceInfo]
  implicit val chatCompletionResponseFormat: Format[ChatCompletionResponse] =
    Json.format[ChatCompletionResponse]

  implicit val chatToolCompletionChoiceInfoReads: Reads[ChatToolCompletionChoiceInfo] =
    Json.reads[ChatToolCompletionChoiceInfo]
  implicit val chatToolCompletionResponseReads: Reads[ChatToolCompletionResponse] =
    Json.reads[ChatToolCompletionResponse]

  implicit val chatFunCompletionChoiceInfoFormat: Format[ChatFunCompletionChoiceInfo] =
    Json.format[ChatFunCompletionChoiceInfo]
  implicit val chatFunCompletionResponseFormat: Format[ChatFunCompletionResponse] =
    Json.format[ChatFunCompletionResponse]

  implicit val chatChunkMessageFormat: Format[ChunkMessageSpec] =
    Json.format[ChunkMessageSpec]
  implicit val chatCompletionChoiceChunkInfoFormat: Format[ChatCompletionChoiceChunkInfo] =
    Json.format[ChatCompletionChoiceChunkInfo]
  implicit val chatCompletionChunkResponseFormat: Format[ChatCompletionChunkResponse] =
    Json.format[ChatCompletionChunkResponse]

  implicit val textEditChoiceInfoFormat: Format[TextEditChoiceInfo] =
    Json.format[TextEditChoiceInfo]
  implicit val textEditFormat: Format[TextEditResponse] =
    Json.format[TextEditResponse]

  implicit val imageFormat: Format[ImageInfo] = Json.format[ImageInfo]

  implicit val embeddingInfoFormat: Format[EmbeddingInfo] =
    Json.format[EmbeddingInfo]
  implicit val embeddingUsageInfoFormat: Format[EmbeddingUsageInfo] =
    Json.format[EmbeddingUsageInfo]
  implicit val embeddingFormat: Format[EmbeddingResponse] =
    Json.format[EmbeddingResponse]

  implicit val fileStatisticsFormat: Format[FileStatistics] = Json.format[FileStatistics]
  implicit val fileInfoFormat: Format[FileInfo] = Json.format[FileInfo]

  implicit val fineTuneEventFormat: Format[FineTuneEvent] = {
    implicit val stringAnyMapFormat: Format[Map[String, Any]] = JsonUtil.StringAnyMapFormat
    Json.format[FineTuneEvent]
  }

  implicit val eitherIntStringFormat: Format[Either[Int, String]] =
    JsonUtil.eitherFormat[Int, String]
  implicit val fineTuneHyperparamsFormat: Format[FineTuneHyperparams] =
    Json.format[FineTuneHyperparams]
  implicit val fineTuneErrorFormat: Format[FineTuneError] = Json.format[FineTuneError]
  implicit val fineTuneFormat: Format[FineTuneJob] = Json.format[FineTuneJob]

  // somehow ModerationCategories.unapply is not working in Scala3
  implicit val moderationCategoriesFormat: Format[ModerationCategories] = (
    (__ \ "hate").format[Boolean] and
      (__ \ "hate/threatening").format[Boolean] and
      (__ \ "self-harm").format[Boolean] and
      (__ \ "sexual").format[Boolean] and
      (__ \ "sexual/minors").format[Boolean] and
      (__ \ "violence").format[Boolean] and
      (__ \ "violence/graphic").format[Boolean]
  )(
    ModerationCategories.apply,
    { (x: ModerationCategories) =>
      (
        x.hate,
        x.hate_threatening,
        x.self_harm,
        x.sexual,
        x.sexual_minors,
        x.violence,
        x.violence_graphic
      )
    }
  )

  // somehow ModerationCategoryScores.unapply is not working in Scala3
  implicit val moderationCategoryScoresFormat: Format[ModerationCategoryScores] = (
    (__ \ "hate").format[Double] and
      (__ \ "hate/threatening").format[Double] and
      (__ \ "self-harm").format[Double] and
      (__ \ "sexual").format[Double] and
      (__ \ "sexual/minors").format[Double] and
      (__ \ "violence").format[Double] and
      (__ \ "violence/graphic").format[Double]
  )(
    ModerationCategoryScores.apply,
    { (x: ModerationCategoryScores) =>
      (
        x.hate,
        x.hate_threatening,
        x.self_harm,
        x.sexual,
        x.sexual_minors,
        x.violence,
        x.violence_graphic
      )
    }
  )

  implicit val moderationResultFormat: Format[ModerationResult] =
    Json.format[ModerationResult]
  implicit val moderationFormat: Format[ModerationResponse] =
    Json.format[ModerationResponse]
  implicit val threadMessageFormat: Format[ThreadMessage] =
    Json.format[ThreadMessage]
  implicit val threadFormat: Format[Thread] =
    Json.format[Thread]

  implicit val fileIdFormat: Format[FileId] =
    Json.format[FileId]

  implicit val threadMessageContentTypeFormat: Format[ThreadMessageContentType] =
    enumFormat[ThreadMessageContentType](
      ThreadMessageContentType.image_file,
      ThreadMessageContentType.text
    )

  implicit val fileAnnotationTypeFormat: Format[FileAnnotationType] =
    enumFormat[FileAnnotationType](
      FileAnnotationType.file_citation,
      FileAnnotationType.file_path
    )

  implicit val fileCitationFormat: Format[FileCitation] =
    Json.format[FileCitation]

  implicit val threadMessageTextFormat: Format[ThreadMessageText] =
    Json.format[ThreadMessageText]

  implicit val threadMessageContentFormat: Format[ThreadMessageContent] =
    Json.format[ThreadMessageContent]

  implicit val fileAnnotationFormat: Format[FileAnnotation] =
    Json.format[FileAnnotation]

  implicit val threadFullMessageFormat: Format[ThreadFullMessage] =
    Json.format[ThreadFullMessage]

  implicit val threadMessageFileFormat: Format[ThreadMessageFile] =
    Json.format[ThreadMessageFile]

  implicit val assistantIdFormat: Format[AssistantId] = Json.valueFormat[AssistantId]

  implicit val assistantFormat: Format[Assistant] = Json.format[Assistant]

  lazy implicit val assistantFileFormat: Format[AssistantFile] = Json.format[AssistantFile]

}
