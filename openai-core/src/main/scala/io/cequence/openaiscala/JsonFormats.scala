package io.cequence.openaiscala

import io.cequence.openaiscala.domain.AssistantToolResource.{
  CodeInterpreterResources,
  FileSearchResources
}
import io.cequence.openaiscala.domain.Batch._
import io.cequence.openaiscala.domain.ChunkingStrategy.StaticChunkingStrategy
import io.cequence.openaiscala.domain.FineTune.WeightsAndBiases
import io.cequence.openaiscala.domain.ToolChoice.EnforcedTool
import io.cequence.openaiscala.domain.StepDetail.{MessageCreation, ToolCalls}
import io.cequence.openaiscala.domain.response.AssistantToolResourceResponse.{
  CodeInterpreterResourcesResponse,
  FileSearchResourcesResponse
}
import io.cequence.openaiscala.domain.response.ResponseFormat.{
  JsonObjectResponse,
  StringResponse,
  TextResponse
}
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{ThreadMessageFile, _}
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.JsonUtil.{enumFormat, snakeEnumFormat}
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.toJson
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Format, JsValue, Json, _}

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

  implicit lazy val contentWrites: Writes[Content] = Writes[Content] {
    _ match {
      case c: TextContent =>
        Json.obj("type" -> "text", "text" -> c.text)

      case c: ImageURLContent =>
        Json.obj("type" -> "image_url", "image_url" -> Json.obj("url" -> c.url))
    }
  }

  implicit lazy val contentReads: Reads[Content] = Reads[Content] { (json: JsValue) =>
    (json \ "type").validate[String].flatMap {
      case "text" => (json \ "text").validate[String].map(TextContent.apply)
      case "image_url" =>
        (json \ "image_url" \ "url").validate[String].map(ImageURLContent.apply)
      case _ => JsError("Invalid type")
    }
  }

  implicit val functionCallSpecFormat: Format[FunctionCallSpec] =
    Json.format[FunctionCallSpec]

  implicit val systemMessageFormat: Format[SystemMessage] = Json.format[SystemMessage]
  implicit val userMessageFormat: Format[UserMessage] = Json.format[UserMessage]
  implicit val userSeqMessageFormat: Format[UserSeqMessage] = Json.format[UserSeqMessage]

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
          "strict" -> fs.strict,
          "parameters" -> fs.parameters
        )
      )
    }

    val assistantsFunctionSpecReads: Reads[FunctionSpec] = (
      (JsPath \ "function" \ "name").read[String] and
        (JsPath \ "function" \ "description").readNullable[String] and
        (JsPath \ "function" \ "strict").readNullable[Boolean] and
        (JsPath \ "function" \ "parameters").read[Map[String, Any]]
    )(FunctionSpec.apply _)

    Format(assistantsFunctionSpecReads, assistantsFunctionSpecWrites)
  }

  implicit lazy val messageToolFormat: Format[MessageTool] = {
    val typeDiscriminatorKey = "type"

    Format[MessageTool](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap {
          case "code_interpreter" => JsSuccess(CodeInterpreterSpec)
          case "file_search"      => JsSuccess(FileSearchSpec)
          case _                  => JsError("Unknown type")
        }
      },
      { (tool: MessageTool) =>
        tool match {
          case CodeInterpreterSpec => Json.obj(typeDiscriminatorKey -> "code_interpreter")
          case FileSearchSpec      => Json.obj(typeDiscriminatorKey -> "file_search")
        }
      }
    )
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
            case CodeInterpreterSpec      => "code_interpreter"
            case FileSearchSpec           => "file_search"
            case FunctionSpec(_, _, _, _) => "function"
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

  implicit val messageReads: Reads[BaseMessage] = Reads { (json: JsValue) =>
    val role = (json \ "role").as[ChatRole]
    (json \ "name").asOpt[String]

    val message: BaseMessage = role match {
      case ChatRole.System => json.as[SystemMessage]

      case ChatRole.User =>
        json.asOpt[UserMessage] match {
          case Some(userMessage) => userMessage
          case None              => json.as[UserSeqMessage]
        }

      case ChatRole.Tool =>
        json.asOpt[AssistantToolMessage] match {
          case Some(assistantToolMessage) => assistantToolMessage
          case None                       => json.as[ToolMessage]
        }

      case ChatRole.Assistant =>
        json.asOpt[AssistantToolMessage] match {
          case Some(assistantToolMessage) => assistantToolMessage
          case None =>
            json.asOpt[AssistantMessage] match {
              case Some(assistantMessage) => assistantMessage
              case None                   => json.as[AssistantFunMessage]
            }
        }

      case ChatRole.Function => json.as[FunMessage]
    }

    JsSuccess(message)
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

  implicit lazy val assistantToolOutputFormat: Format[AssistantToolOutput] =
    Json.format[AssistantToolOutput]

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

  implicit val byteArrayReads: Reads[Seq[Byte]] = new Reads[Seq[Byte]] {

    /**
     * Parses a JSON representation of a `Seq[Byte]` into a `JsResult[Seq[Byte]]`. This method
     * expects the JSON to be an array of numbers, where each number represents a valid byte
     * value (between -128 and 127, inclusive). If the JSON structure is correct and all
     * numbers are valid byte values, it returns a `JsSuccess` containing the sequence of
     * bytes. Otherwise, it returns a `JsError` detailing the parsing issue encountered.
     *
     * @param json
     *   The `JsValue` to be parsed, expected to be a `JsArray` of `JsNumber`.
     * @return
     *   A `JsResult[Seq[Byte]]` which is either a `JsSuccess` containing the parsed sequence
     *   of bytes, or a `JsError` with parsing error details.
     */
    def reads(json: JsValue): JsResult[Seq[Byte]] = json match {
      case JsArray(elements) =>
        try {
          JsSuccess(elements.map {
            case JsNumber(n) if n.isValidInt => n.toIntExact.toByte
            case _ => throw new RuntimeException("Invalid byte value")
          }.toIndexedSeq)
        } catch {
          case e: Exception => JsError("Error parsing byte array: " + e.getMessage)
        }
      case _ => JsError("Expected JSON array for byte array")
    }
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
  implicit lazy val fineTuneMetricsFormat: Format[Metrics] = Json.format[Metrics]
  implicit lazy val fineTuneCheckpointFormat: Format[FineTuneCheckpoint] =
    Json.format[FineTuneCheckpoint]
  implicit lazy val fineTuneHyperparamsFormat: Format[FineTuneHyperparams] =
    Json.format[FineTuneHyperparams]
  implicit lazy val fineTuneErrorFormat: Format[FineTuneError] = Json.format[FineTuneError]

  implicit lazy val fineTuneIntegrationFormat: Format[FineTune.Integration] = {
    val typeDiscriminatorKey = "type"
    val weightsAndBiasesType = "wandb"
    implicit val weightsAndBiasesIntegrationFormat: Format[WeightsAndBiases] =
      Json.format[WeightsAndBiases]

    Format[FineTune.Integration](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap { case `weightsAndBiasesType` =>
          (json \ weightsAndBiasesType)
            .validate[WeightsAndBiases](weightsAndBiasesIntegrationFormat)
        }
      },
      { (integration: FineTune.Integration) =>
        val commonJson = Json.obj {
          val discriminatorValue = integration match {
            case _: WeightsAndBiases => weightsAndBiasesType
          }
          typeDiscriminatorKey -> discriminatorValue
        }
        integration match {
          case integration: WeightsAndBiases =>
            commonJson ++ JsObject(
              Seq(
                weightsAndBiasesType -> weightsAndBiasesIntegrationFormat.writes(integration)
              )
            )
        }
      }
    )
  }

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

  implicit lazy val assistantToolResourceResponsesFormat
    : Reads[Seq[AssistantToolResourceResponse]] = Reads { json =>
    val codeInterpreter = (json \ "code_interpreter" \ "file_ids")
      .asOpt[Seq[FileId]]
      .map(CodeInterpreterResourcesResponse.apply)

    val fileSearch = (json \ "file_search" \ "vector_store_ids")
      .asOpt[Seq[String]]
      .map(FileSearchResourcesResponse.apply)

    Seq(codeInterpreter, fileSearch).flatten match {
      case Nil       => JsError("Expected code_interpreter or file_search response")
      case responses => JsSuccess(responses)
    }
  }

  implicit lazy val assistantToolResourceResponsesWrites
    : Writes[Seq[AssistantToolResourceResponse]] = Writes { items =>
    items.map {
      case c: CodeInterpreterResourcesResponse =>
        Json.obj("code_interpreter" -> Json.obj("file_ids" -> c.file_ids))

      case f: FileSearchResourcesResponse =>
        Json.obj("file_search" -> Json.obj("vector_store_ids" -> f.vector_store_ids))
    }.foldLeft(Json.obj())(_ ++ _)
  }

  implicit lazy val assistantToolResourceWrites: Writes[AssistantToolResource] = {
    case c: CodeInterpreterResources =>
      Json.obj("code_interpreter" -> Json.obj("file_ids" -> c.fileIds))

    case f: FileSearchResources =>
      assert(
        f.vectorStoreIds.isEmpty || f.vectorStores.isEmpty,
        "Only one of vector_store_ids or vector_stores should be provided."
      )

      val vectorStoreIdsJson =
        if (f.vectorStoreIds.nonEmpty) Json.obj("vector_store_ids" -> f.vectorStoreIds)
        else Json.obj()

      val vectorStoresJson =
        if (f.vectorStores.nonEmpty) Json.obj("vector_stores" -> f.vectorStores)
        else Json.obj()

      Json.obj("file_search" -> (vectorStoreIdsJson ++ vectorStoresJson))
  }

  implicit lazy val assistantToolResourceReads: Reads[AssistantToolResource] = Reads { json =>
    (json \ "code_interpreter").toOption.map { codeInterpreterJson =>
      (codeInterpreterJson \ "file_ids")
        .validate[Seq[FileId]]
        .map(CodeInterpreterResources.apply)
    }.orElse(
      (json \ "file_search").toOption.map { fileSearchJson =>
        val fileSearchVectorStoreIds = (fileSearchJson \ "vector_store_ids").asOpt[Seq[String]]

        val fileSearchVectorStores = (json \ "file_search" \ "vector_stores")
          .asOpt[Seq[AssistantToolResource.VectorStore]]

        JsSuccess(
          FileSearchResources(
            fileSearchVectorStoreIds.getOrElse(Nil),
            fileSearchVectorStores.getOrElse(Nil)
          )
        )
      }
    ).getOrElse(
      JsError("Expected code_interpreter or file_search")
    )
  }

  implicit lazy val threadReads: Reads[Thread] =
    (
      (__ \ "id").read[String] and
        (__ \ "created_at").read[ju.Date] and
        (__ \ "tool_resources")
          .read[Seq[AssistantToolResourceResponse]]
          .orElse(Reads.pure(Nil)) and
        (__ \ "metadata").read[Map[String, String]].orElse(Reads.pure(Map()))
    )(Thread.apply _)

//  implicit lazy val threadWrites: Writes[Thread] = Json.writes[Thread]

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

  implicit lazy val threadFullMessageReads: Reads[ThreadFullMessage] =
    (
      (__ \ "id").read[String] and
        (__ \ "created_at").read[ju.Date] and
        (__ \ "thread_id").read[String] and
        (__ \ "role").read[ChatRole].orElse(Reads.pure(ChatRole.User)) and
        (__ \ "content").read[Seq[ThreadMessageContent]].orElse(Reads.pure(Nil)) and
        (__ \ "assistant_id").readNullable[String] and
        (__ \ "run_id").readNullable[String] and
        (__ \ "attachments").read[Seq[Attachment]].orElse(Reads.pure(Nil)) and
        (__ \ "metadata").read[Map[String, String]].orElse(Reads.pure(Map()))
    )(ThreadFullMessage.apply _)

  implicit lazy val threadFullMessageWrites: Writes[ThreadFullMessage] =
    Json.writes[ThreadFullMessage]

  implicit lazy val threadMessageFileFormat: Format[ThreadMessageFile] =
    Json.format[ThreadMessageFile]

  implicit lazy val assistantToolResourceVectorStoreFormat
    : Format[AssistantToolResource.VectorStore] = {
    implicit val stringStringMapFormat: Format[Map[String, String]] =
      JsonUtil.StringStringMapFormat
    (
      (__ \ "file_ids").format[Seq[FileId]] and
        (__ \ "metadata").format[Map[String, String]]
    )(
      AssistantToolResource.VectorStore.apply,
      unlift(AssistantToolResource.VectorStore.unapply)
    )
  }

  implicit lazy val codeInterpreterResourcesResponseFormat
    : Format[CodeInterpreterResourcesResponse] =
    Json.format[CodeInterpreterResourcesResponse]

  implicit lazy val fileSearchResourcesResponseFormat: Format[FileSearchResourcesResponse] =
    Json.format[FileSearchResourcesResponse]

//  implicit lazy val assistantToolResourceFormat: Format[AssistantToolResource] =
//    Format(assistantToolResourcesReads, assistantToolResourcesWrites)

//  implicit lazy val assistantToolResourceResponseWrites
//    : Writes[AssistantToolResourceResponse] = {
//    case c: CodeInterpreterResourcesResponse =>
//      Json.obj(
//        "code_interpreter" -> Json.toJson(c)(codeInterpreterResourcesResponseFormat)
//      )
//    case f: FileSearchResourcesResponse =>
//      Json.obj("file_search" -> Json.toJson(f)(fileSearchResourcesResponseFormat))
//  }

//  implicit lazy val assistantToolResourceResponseFormat
//    : Format[AssistantToolResourceResponse] =
//    Format(assistantToolResourceResponseReads, assistantToolResourceResponseWrites)

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

  implicit lazy val attachmentFormat: Format[Attachment] = (
    (__ \ "file_id").formatNullable[FileId] and
      (__ \ "tools").format[Seq[MessageTool]]
  )(Attachment.apply, unlift(Attachment.unapply))

  implicit lazy val assistantReads: Reads[Assistant] = (
    (__ \ "id").read[String] and
      (__ \ "created_at").read[ju.Date] and
      (__ \ "name").readNullable[String] and
      (__ \ "description").readNullable[String] and
      (__ \ "model").read[String] and
      (__ \ "instructions").readNullable[String] and
      (__ \ "tools").read[List[AssistantTool]] and
      (__ \ "tool_resources")
        .read[Seq[AssistantToolResourceResponse]]
        .orElse(Reads.pure(Nil)) and
      (__ \ "metadata").read[Map[String, String]].orElse(Reads.pure(Map())) and
      (__ \ "temperature").readNullable[Double].orElse(Reads.pure(None)) and
      (__ \ "top_p").readNullable[Double].orElse(Reads.pure(None)) and
      (__ \ "response_format").read[ResponseFormat]
  )((Assistant.apply _))

//  implicit lazy val assistantWrites: Writes[Assistant] =
//    Json.writes[Assistant]

  implicit lazy val batchEndPointFormat: Format[BatchEndpoint] = enumFormat[BatchEndpoint](
    BatchEndpoint.`/v1/chat/completions`,
    BatchEndpoint.`/v1/embeddings`
  )

  implicit lazy val completionWindowFormat: Format[CompletionWindow] =
    enumFormat[CompletionWindow](
      CompletionWindow.`24h`
    )

  implicit lazy val batchProcessingErrorFormat: Format[BatchProcessingError] =
    Json.format[BatchProcessingError]
  implicit lazy val batchProcessingErrorsFormat: Format[BatchProcessingErrors] =
    Json.format[BatchProcessingErrors]
  implicit lazy val batchFormat: Format[Batch] = Json.format[Batch]
  implicit lazy val batchInputFormat: Format[BatchRow] = Json.format[BatchRow]

  implicit lazy val chatCompletionBatchResponseFormat: Format[ChatCompletionBatchResponse] =
    Json.format[ChatCompletionBatchResponse]
  implicit lazy val embeddingBatchResponseFormat: Format[EmbeddingBatchResponse] =
    Json.format[EmbeddingBatchResponse]

  implicit lazy val batchResponseFormat: Format[BatchResponse] = {
    val reads: Reads[BatchResponse] = Reads { json =>
      chatCompletionBatchResponseFormat
        .reads(json)
        .orElse(embeddingBatchResponseFormat.reads(json))
    }

    val writes: Writes[BatchResponse] = Writes {
      case chatCompletionResponse: ChatCompletionResponse =>
        chatCompletionResponseFormat.writes(chatCompletionResponse)
      case embeddingResponse: EmbeddingResponse =>
        embeddingFormat.writes(embeddingResponse)
    }

    Format(reads, writes)
  }

  implicit lazy val batchErrorFormat: Format[BatchError] = Json.format[BatchError]
  implicit lazy val createBatchResponseFormat: Format[CreateBatchResponse] =
    Json.format[CreateBatchResponse]
  implicit lazy val createBatchResponsesFormat: Format[CreateBatchResponses] =
    Json.format[CreateBatchResponses]

  implicit lazy val fileCountsFormat: Format[FileCounts] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[FileCounts]
  }

  implicit lazy val vectorStoreFormat: Format[VectorStore] =
    Json.format[VectorStore]

  implicit lazy val vectorStoreFileFormat: Format[VectorStoreFile] = {
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[VectorStoreFile]
  }

  implicit lazy val vectorStoreFileStatusFormat: Format[VectorStoreFileStatus] = {
    import VectorStoreFileStatus._
    enumFormat(Cancelled, Completed, InProgress, Failed)
  }

  implicit lazy val lastErrorFormat: Format[LastError] = Json.format[LastError]
  implicit lazy val lastErrorCodeFormat: Format[LastErrorCode] = {
    import LastErrorCode._
    snakeEnumFormat(ServerError, RateLimitExceeded)
  }
  implicit lazy val chunkingStrategyAutoFormat
    : Format[ChunkingStrategy.AutoChunkingStrategy.type] =
    Json.format[ChunkingStrategy.AutoChunkingStrategy.type]
  implicit lazy val chunkingStrategyStaticFormat
    : Format[ChunkingStrategy.StaticChunkingStrategy.type] =
    Json.format[ChunkingStrategy.StaticChunkingStrategy.type]

  val chunkingStrategyFormatReads: Reads[ChunkingStrategy] =
    (
      (__ \ "max_chunk_size_tokens").readNullable[Int] and
        (__ \ "chunk_overlap_tokens").readNullable[Int]
    )(
      (
        maxChunkSizeTokens: Option[Int],
        chunkOverlapTokens: Option[Int]
      ) => StaticChunkingStrategy(maxChunkSizeTokens, chunkOverlapTokens)
    )

  implicit lazy val chunkingStrategyFormat: Format[ChunkingStrategy] = {
    val reads: Reads[ChunkingStrategy] = Reads { json =>
      import ChunkingStrategy._
      (json \ "type").validate[String].flatMap {
        case "auto" => JsSuccess(AutoChunkingStrategy)
        case "static" =>
          (json.validate[ChunkingStrategy](chunkingStrategyFormatReads))
        case "" => JsSuccess(AutoChunkingStrategy)
        case _  => JsError("Unknown chunking strategy type")
      }
    }

    val writes: Writes[ChunkingStrategy] = Writes {
      case ChunkingStrategy.AutoChunkingStrategy => Json.obj("type" -> "auto")
      case ChunkingStrategy.StaticChunkingStrategy(maxChunkSizeTokens, chunkOverlapTokens) =>
        Json.obj(
          "type" -> "static",
          "max_chunk_size_tokens" -> maxChunkSizeTokens,
          "chunk_overlap_tokens" -> chunkOverlapTokens
        )
    }

    Format(reads, writes)
  }

  implicit lazy val runReasonFormat: Format[Run.Reason] = Json.format[Run.Reason]

  implicit lazy val lastRunErrorCodeFormat: Format[Run.LastErrorCode] = {
    import Run.LastErrorCode._
    snakeEnumFormat(ServerError, RateLimitExceeded, InvalidPrompt)
  }

  implicit lazy val truncationStrategyTypeFormat: Format[Run.TruncationStrategyType] = {
    import Run.TruncationStrategyType._
    snakeEnumFormat(Auto, LastMessages)
  }

  implicit lazy val runStatusFormat: Format[RunStatus] = {
    import RunStatus._
    snakeEnumFormat(
      Queued,
      InProgress,
      RequiresAction,
      Cancelling,
      Cancelled,
      Failed,
      Completed,
      Incomplete,
      Expired
    )
  }

  implicit lazy val runFormat: Format[Run] = Json.format[Run]

  implicit val toolChoiceFormat: Format[ToolChoice] = {
    import ToolChoice._

    val enforcedToolReads: Reads[EnforcedTool] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "code_interpreter" => JsSuccess(EnforcedTool(CodeInterpreterSpec))
        case "file_search"      => JsSuccess(EnforcedTool(FileSearchSpec))
        case "function" => {
          val functionSpec = (json \ "function").as[FunctionSpec]
          JsSuccess(EnforcedTool(functionSpec))
        }
        case _ => JsError("Unknown type")
      }
    }

    val reads: Reads[ToolChoice] = Reads { json =>
      json.validate[String].flatMap {
        case "none"     => JsSuccess(None)
        case "auto"     => JsSuccess(Auto)
        case "required" => JsSuccess(Required)
        case _          => enforcedToolReads.reads(json)
      }
    }

    val writes: Writes[ToolChoice] = Writes {
      case None                              => JsString("none")
      case Auto                              => JsString("auto")
      case Required                          => JsString("required")
      case EnforcedTool(CodeInterpreterSpec) => Json.obj("type" -> "code_interpreter")
      case EnforcedTool(FileSearchSpec)      => Json.obj("type" -> "file_search")
      case EnforcedTool(FunctionSpec(name, _, _, _)) =>
        Json.obj("type" -> "function", "function" -> Json.obj("name" -> name))
    }

    Format(reads, writes)
  }

  implicit lazy val runResponseFormat: Format[RunResponse] = Json.format[RunResponse]

  implicit lazy val toolCallFormat: Format[ToolCall] = Json.format[ToolCall]
  implicit lazy val submitToolOutputsFormat: Format[SubmitToolOutputs] =
    Json.format[SubmitToolOutputs]
  implicit lazy val requiredActionFormat: Format[RequiredAction] = Json.format[RequiredAction]

//  implicit lazy val runStepLastErrorFormat: Format[RunStep.LastError] =
//    Json.format[RunStep.LastError]
//
//  implicit lazy val runStepLastErrorCodeFormat: Format[RunStep.LastErrorCode] = {
//    import RunStep.LastErrorCode._
//    snakeEnumFormat[RunStep.LastErrorCode](ServerError, RateLimitExceeded)
//  }
//
//  implicit lazy val runStepLastErrorFormat: Format[RunStep.LastError] = {
//    format[RunStep.LastError]
//  }

  implicit lazy val runStepFormat: Format[RunStep] = {
    implicit val jsonConfig: JsonConfiguration = JsonConfiguration(SnakeCase)
    Json.format[RunStep]
  }

  implicit val messageCreationReads: Reads[MessageCreation] =
    (__ \ "message_creation" \ "message_id").read[String].map(MessageCreation.apply)

  implicit val messageCreationWrites: Writes[MessageCreation] = Writes { messageCreation =>
    Json.obj("message_creation" -> Json.obj("message_id" -> messageCreation.messageId))
  }

  implicit val messageCreationFormat: Format[MessageCreation] =
    Format(messageCreationReads, messageCreationWrites)

  implicit val toolCallsFormat: Format[ToolCalls] = Json.format[ToolCalls]

  implicit val stepDetailFormat: Format[StepDetail] = {
    implicit val jsonConfig: JsonConfiguration = JsonConfiguration(SnakeCase)

    implicit val stepDetailReads: Reads[StepDetail] = Reads[StepDetail] { json =>
      (json \ "type").as[String] match {
        case "message_creation" => messageCreationFormat.reads(json)
        case "tool_calls"       => toolCallsFormat.reads(json)
      }
    }

    implicit val stepDetailWrites: Writes[StepDetail] = Writes[StepDetail] {
      case mc: MessageCreation =>
        messageCreationFormat.writes(mc).as[JsObject] + ("type" -> JsString("MessageCreation"))
      case tc: ToolCalls =>
        toolCallsFormat.writes(tc).as[JsObject] + ("type" -> JsString("ToolCalls"))
    }

    Format(stepDetailReads, stepDetailWrites)
  }

}
