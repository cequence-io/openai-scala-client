package io.cequence.openaiscala.v2

import io.cequence.openaiscala.JsonUtil
import io.cequence.openaiscala.JsonUtil.enumFormat
import io.cequence.openaiscala.v2.domain.AssistantToolResource.{
  CodeInterpreterResources,
  FileSearchResources,
  VectorStore
}
import io.cequence.openaiscala.v2.domain.response.AssistantToolResourceResponse.{
  CodeInterpreterResourcesResponse,
  FileSearchResourcesResponse
}
import io.cequence.openaiscala.v2.domain.response.ResponseFormat.{
  JsonObjectResponse,
  StringResponse,
  TextResponse
}
import io.cequence.openaiscala.v2.domain.response._
import io.cequence.openaiscala.v2.domain.{ThreadMessageFile, _}
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Json, _}

import java.{util => ju}

object JsonFormats {
  private implicit lazy val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  implicit lazy val permissionFormat: Format[Permission] = Json.format[Permission]
  implicit lazy val modelSpecFormat: Format[ModelInfo] = {
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

  implicit lazy val usageInfoFormat: Format[UsageInfo] = Json.format[UsageInfo]

  private implicit lazy val stringDoubleMapFormat: Format[Map[String, Double]] =
    JsonUtil.StringDoubleMapFormat
  private implicit lazy val stringStringMapFormat: Format[Map[String, String]] =
    JsonUtil.StringStringMapFormat

  implicit lazy val logprobsInfoFormat: Format[LogprobsInfo] =
    Json.format[LogprobsInfo]
  implicit lazy val textCompletionChoiceInfoFormat: Format[TextCompletionChoiceInfo] =
    Json.format[TextCompletionChoiceInfo]
  implicit lazy val textCompletionFormat: Format[TextCompletionResponse] =
    Json.format[TextCompletionResponse]

  implicit lazy val chatRoleFormat: Format[ChatRole] = enumFormat[ChatRole](
    ChatRole.User,
    ChatRole.System,
    ChatRole.Assistant,
    ChatRole.Function,
    ChatRole.Tool
  )

  implicit lazy val functionCallSpecFormat: Format[FunctionCallSpec] =
    Json.format[FunctionCallSpec]

  implicit lazy val systemMessageFormat: Format[SystemMessage] = Json.format[SystemMessage]
  implicit lazy val userMessageFormat: Format[UserMessage] = Json.format[UserMessage]
  implicit lazy val toolMessageFormat: Format[ToolMessage] = Json.format[ToolMessage]
  implicit lazy val assistantMessageFormat: Format[AssistantMessage] =
    Json.format[AssistantMessage]
  implicit lazy val assistantToolMessageReads: Reads[AssistantToolMessage] = (
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
  implicit lazy val assistantFunMessageFormat: Format[AssistantFunMessage] =
    Json.format[AssistantFunMessage]

  implicit lazy val funMessageFormat: Format[FunMessage] = Json.format[FunMessage]

  implicit lazy val messageSpecFormat: Format[MessageSpec] = Json.format[MessageSpec]

  implicit lazy val functionSpecFormat: Format[FunctionSpec] = {
    // use just here for FunctionSpec
    implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] =
      JsonUtil.StringAnyMapFormat
    Json.format[FunctionSpec]
  }

  val assistantsFunctionSpecFormat: Format[FunctionSpec] = {
    implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] =
      JsonUtil.StringAnyMapFormat

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

  implicit lazy val assistantToolFormat: Format[AssistantTool] = {
    val typeDiscriminatorKey = "type"

    Format[AssistantTool](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap {
          case "code_interpreter" => JsSuccess(CodeInterpreterSpec)
          case "file_search"      => JsSuccess(FileSearchSpec)
          case "function"         => json.validate[FunctionSpec](assistantsFunctionSpecFormat)
          case _                  => JsError("Unknown type")
        }
      },
      { (tool: AssistantTool) =>
        val commonJson = Json.obj {
          val discriminatorValue = tool match {
            case CodeInterpreterSpec   => "code_interpreter"
            case FileSearchSpec        => "file_search"
            case FunctionSpec(_, _, _) => "function"
          }
          typeDiscriminatorKey -> discriminatorValue
        }
        tool match {
          case CodeInterpreterSpec => commonJson
          case FileSearchSpec      => commonJson
          case ft: FunctionSpec =>
            commonJson ++ Json.toJson(ft)(assistantsFunctionSpecFormat).as[JsObject]
        }
      }
    )
  }

  implicit lazy val contentWrites: Writes[Content] = Writes[Content] {
    _ match {
      case c: TextContent =>
        Json.obj("type" -> "text", "text" -> c.text)

      case c: ImageURLContent =>
        Json.obj("type" -> "image_url", "image_url" -> Json.obj("url" -> c.url))
    }
  }

  implicit lazy val messageWrites: Writes[BaseMessage] = Writes { (message: BaseMessage) =>
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

  implicit lazy val toolWrites: Writes[ToolSpec] = Writes[ToolSpec] {
    _ match {
      case x: FunctionSpec =>
        Json.obj("type" -> "function", "function" -> Json.toJson(x))
    }
  }

  implicit lazy val topLogprobInfoormat: Format[TopLogprobInfo] = {
    val reads: Reads[TopLogprobInfo] = (
      (__ \ "token").read[String] and
        (__ \ "logprob").read[Double] and
        (__ \ "bytes").read[Seq[Short]].orElse(Reads.pure(Nil))
    )(TopLogprobInfo.apply _)

    val writes: Writes[TopLogprobInfo] = Json.writes[TopLogprobInfo]
    Format(reads, writes)
  }

  implicit lazy val logprobInfoFormat: Format[LogprobInfo] =
    Json.format[LogprobInfo]
  implicit lazy val logprobsFormat: Format[Logprobs] =
    Json.format[Logprobs]

  implicit lazy val chatCompletionChoiceInfoFormat: Format[ChatCompletionChoiceInfo] =
    Json.format[ChatCompletionChoiceInfo]
  implicit lazy val chatCompletionResponseFormat: Format[ChatCompletionResponse] =
    Json.format[ChatCompletionResponse]

  implicit lazy val chatToolCompletionChoiceInfoReads: Reads[ChatToolCompletionChoiceInfo] =
    Json.reads[ChatToolCompletionChoiceInfo]
  implicit lazy val chatToolCompletionResponseReads: Reads[ChatToolCompletionResponse] =
    Json.reads[ChatToolCompletionResponse]

  implicit lazy val chatFunCompletionChoiceInfoFormat: Format[ChatFunCompletionChoiceInfo] =
    Json.format[ChatFunCompletionChoiceInfo]
  implicit lazy val chatFunCompletionResponseFormat: Format[ChatFunCompletionResponse] =
    Json.format[ChatFunCompletionResponse]

  implicit lazy val chatChunkMessageFormat: Format[ChunkMessageSpec] =
    Json.format[ChunkMessageSpec]
  implicit lazy val chatCompletionChoiceChunkInfoFormat
    : Format[ChatCompletionChoiceChunkInfo] =
    Json.format[ChatCompletionChoiceChunkInfo]
  implicit lazy val chatCompletionChunkResponseFormat: Format[ChatCompletionChunkResponse] =
    Json.format[ChatCompletionChunkResponse]

  implicit lazy val textEditChoiceInfoFormat: Format[TextEditChoiceInfo] =
    Json.format[TextEditChoiceInfo]
  implicit lazy val textEditFormat: Format[TextEditResponse] =
    Json.format[TextEditResponse]

  implicit lazy val imageFormat: Format[ImageInfo] = Json.format[ImageInfo]

  implicit lazy val embeddingInfoFormat: Format[EmbeddingInfo] =
    Json.format[EmbeddingInfo]
  implicit lazy val embeddingUsageInfoFormat: Format[EmbeddingUsageInfo] =
    Json.format[EmbeddingUsageInfo]
  implicit lazy val embeddingFormat: Format[EmbeddingResponse] =
    Json.format[EmbeddingResponse]

  implicit lazy val fileStatisticsFormat: Format[FileStatistics] = Json.format[FileStatistics]
  implicit lazy val fileInfoFormat: Format[FileInfo] = Json.format[FileInfo]

  implicit lazy val fineTuneEventFormat: Format[FineTuneEvent] = {
    implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] =
      JsonUtil.StringAnyMapFormat
    Json.format[FineTuneEvent]
  }

  implicit lazy val eitherIntStringFormat: Format[Either[Int, String]] =
    JsonUtil.eitherFormat[Int, String]
  implicit lazy val fineTuneHyperparamsFormat: Format[FineTuneHyperparams] =
    Json.format[FineTuneHyperparams]
  implicit lazy val fineTuneErrorFormat: Format[FineTuneError] = Json.format[FineTuneError]
  implicit lazy val fineTuneFormat: Format[FineTuneJob] = Json.format[FineTuneJob]

  // somehow ModerationCategories.unapply is not working in Scala3
  implicit lazy val moderationCategoriesFormat: Format[ModerationCategories] = (
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
  implicit lazy val moderationCategoryScoresFormat: Format[ModerationCategoryScores] = (
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

  implicit lazy val moderationResultFormat: Format[ModerationResult] =
    Json.format[ModerationResult]
  implicit lazy val moderationFormat: Format[ModerationResponse] =
    Json.format[ModerationResponse]
  implicit lazy val threadMessageFormat: Format[ThreadMessage] =
    Json.format[ThreadMessage]
  implicit lazy val threadFormat: Format[Thread] =
    Json.format[Thread]

  implicit lazy val fileIdFormat: Format[FileId] =
    Json.format[FileId]

  implicit lazy val threadMessageContentTypeFormat: Format[ThreadMessageContentType] =
    enumFormat[ThreadMessageContentType](
      ThreadMessageContentType.image_file,
      ThreadMessageContentType.text
    )

  implicit lazy val fileAnnotationTypeFormat: Format[FileAnnotationType] =
    enumFormat[FileAnnotationType](
      FileAnnotationType.file_citation,
      FileAnnotationType.file_path
    )

  implicit lazy val fileAnnotationFormat: Format[FileAnnotation] =
    Json.format[FileAnnotation]

  implicit lazy val fileCitationFormat: Format[FileCitation] =
    Json.format[FileCitation]

  implicit lazy val threadMessageTextFormat: Format[ThreadMessageText] =
    Json.format[ThreadMessageText]

  implicit lazy val threadMessageContentFormat: Format[ThreadMessageContent] =
    Json.format[ThreadMessageContent]

  implicit lazy val threadFullMessageFormat: Format[ThreadFullMessage] =
    Json.format[ThreadFullMessage]

  implicit lazy val threadMessageFileFormat: Format[ThreadMessageFile] =
    Json.format[ThreadMessageFile]

  implicit lazy val assistantIdFormat: Format[AssistantId] = Json.valueFormat[AssistantId]

  implicit lazy val vectorStoreFormat: Format[VectorStore] = {
    implicit val stringStringMapFormat: Format[Map[String, String]] =
      JsonUtil.StringStringMapFormat
    (
      (__ \ "file_ids").format[Seq[FileId]] and
        (__ \ "metadata").format[Map[String, String]]
    )(VectorStore.apply, unlift(VectorStore.unapply))
  }

  implicit lazy val assistantToolResourceWrites: Writes[AssistantToolResource] = {
    case c: CodeInterpreterResources =>
      Json.obj("code_interpreter" -> Json.obj("file_ids" -> c.fileIds))
    case f: FileSearchResources =>
      Json.obj(
        "file_search" -> Json.obj(
          "vector_store_ids" -> f.vectorStoreIds,
          "vector_stores" -> f.vectorStores
        )
      )
  }

  implicit lazy val assistantToolResourceReads: Reads[AssistantToolResource] =
    codeInterpreterReads orElse fileSearchReads

  implicit lazy val codeInterpreterReads: Reads[AssistantToolResource] =
    (JsPath \ "code_interpreter" \ "file_ids")
      .read[Seq[FileId]]
      .map(CodeInterpreterResources(_))
  implicit lazy val fileSearchReads: Reads[AssistantToolResource] = (
    (JsPath \ "file_search" \ "vector_store_ids").read[Seq[FileId]] and
      (JsPath \ "file_search" \ "vector_stores").read[Seq[VectorStore]]
  )(FileSearchResources.apply _)

  implicit lazy val codeInterpreterResourcesResponseFormat
    : Format[CodeInterpreterResourcesResponse] =
    Json.format[CodeInterpreterResourcesResponse]

  implicit lazy val fileSearchResourcesResponseFormat: Format[FileSearchResourcesResponse] =
    Json.format[FileSearchResourcesResponse]

  implicit lazy val assistantToolResourceFormat: Format[AssistantToolResource] =
    Format(assistantToolResourceReads, assistantToolResourceWrites)

  implicit lazy val assistantToolResourceResponseWrites
    : Writes[AssistantToolResourceResponse] = {
    case c: CodeInterpreterResourcesResponse =>
      Json.obj(
        "code_interpreter_response" -> Json.toJson(c)(codeInterpreterResourcesResponseFormat)
      )
    case f: FileSearchResourcesResponse =>
      Json.obj("file_search_response" -> Json.toJson(f)(fileSearchResourcesResponseFormat))
  }

  implicit lazy val assistantToolResourceResponseFormat
    : Format[AssistantToolResourceResponse] =
    Format(assistantToolResourceResponseReads, assistantToolResourceResponseWrites)

  implicit lazy val assistantToolResourceResponseReads: Reads[AssistantToolResourceResponse] =
    codeInterpreterResponseReads orElse fileSearchResponseReads

  implicit lazy val codeInterpreterResponseReads: Reads[AssistantToolResourceResponse] =
    (JsPath \ "code_interpreter_response")
      .read[Seq[FileId]]
      .map(CodeInterpreterResourcesResponse.apply)

  implicit lazy val fileSearchResponseReads: Reads[AssistantToolResourceResponse] =
    (JsPath \ "file_search_response").read[Seq[FileId]].map(FileSearchResourcesResponse.apply)

  implicit lazy val responseFormatFormat: Format[ResponseFormat] = {
    def error(json: JsValue) = JsError(
      s"Expected String response, JSON Object response, or Text response format, but got $json"
    )

    Format(
      Reads {
        case JsString("auto") => JsSuccess(StringResponse)
        case JsObject(fields) =>
          fields
            .get("type")
            .map {
              case JsString("json_object") => JsSuccess(JsonObjectResponse)
              case JsString("text")        => JsSuccess(TextResponse)
              case json                    => error(json)
            }
            .getOrElse(error(JsObject(fields)))
        case json => error(json)
      },
      Writes {
        case StringResponse     => JsString("auto")
        case JsonObjectResponse => Json.obj("type" -> "json_object")
        case TextResponse       => Json.obj("type" -> "text")
      }
    )
  }

  implicit lazy val attachmentFormat: Format[Attachment] = ???

  implicit lazy val assistantFormat: Format[Assistant] = Json.format[Assistant]

}
