package io.cequence.openaiscala

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.Batch._
import io.cequence.openaiscala.domain.ChunkingStrategy.StaticChunkingStrategy
import io.cequence.openaiscala.domain.FineTune.WeightsAndBiases
import io.cequence.openaiscala.domain.ThreadAndRun.Content.ContentBlock.ImageDetail
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  ReasoningEffort,
  ServiceTier
}
import io.cequence.openaiscala.domain.Run.TruncationStrategy
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
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
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

  implicit lazy val completionTokenDetailsFormat: Format[CompletionTokenDetails] =
    Json.format[CompletionTokenDetails]

  implicit lazy val promptTokensDetailsFormat: Format[PromptTokensDetails] =
    Json.format[PromptTokensDetails]

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
    ChatRole.Developer,
    ChatRole.System,
    ChatRole.Assistant,
    ChatRole.Function,
    ChatRole.Tool
  )

  implicit lazy val contentWrites: Writes[Content] = Writes[Content] {
    case c: TextContent =>
      Json.obj("type" -> "text", "text" -> c.text)

    case c: ImageURLContent =>
      Json.obj("type" -> "image_url", "image_url" -> Json.obj("url" -> c.url))
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
  implicit val developerMessageFormat: Format[DeveloperMessage] = Json.format[DeveloperMessage]
  implicit val userMessageFormat: Format[UserMessage] = Json.format[UserMessage]
  implicit val userSeqMessageFormat: Format[UserSeqMessage] = Json.format[UserSeqMessage]

  implicit val toolMessageFormat: Format[ToolMessage] = Json.format[ToolMessage]

  implicit val assistantMessageFormat: Format[AssistantMessage] = Json.format[AssistantMessage]
  implicit val assistantToolMessageReads: Reads[AssistantToolMessage] = (
    (__ \ "content").readNullable[String] and
      (__ \ "name").readNullable[String] and
      (__ \ "tool_calls").readNullable[JsArray]
  ) {
    (
      content,
      name,
      tool_calls
    ) =>
      val idToolCalls = tool_calls.getOrElse(JsArray()).value.toSeq.map { toolCall =>
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

  implicit lazy val chatCompletionToolFormat: Format[FunctionTool] = {
    // use just here for FunctionSpec
    implicit lazy val stringAnyMapFormat: Format[Map[String, Any]] =
      JsonUtil.StringAnyMapFormat
    Json.format[FunctionTool]
  }

  implicit lazy val messageAttachmentToolFormat: Format[MessageAttachmentTool] = {
    val typeDiscriminatorKey = "type"

    Format[MessageAttachmentTool](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap {
          case "code_interpreter" => JsSuccess(MessageAttachmentTool.CodeInterpreterSpec)
          case "file_search"      => JsSuccess(MessageAttachmentTool.FileSearchSpec)
          case _                  => JsError("Unknown type")
        }
      },
      {
        case MessageAttachmentTool.CodeInterpreterSpec =>
          Json.obj(typeDiscriminatorKey -> "code_interpreter")
        case MessageAttachmentTool.FileSearchSpec =>
          Json.obj(typeDiscriminatorKey -> "file_search")
      }
    )
  }

  implicit lazy val assistantToolFormat: Format[AssistantTool] = {
    val typeDiscriminatorKey = "type"
    implicit val mapFormat = JsonUtil.StringAnyMapFormat

    lazy val assistantFunctionToolFormat: Format[AssistantTool.FunctionTool] = {
      val reads = Reads[AssistantTool.FunctionTool] { json =>
        for {
          name <- (json \ "name").validate[String]
          description <- (json \ "description").validateOpt[String]
          parameters <- (json \ "parameters").validate[Map[String, Any]](mapFormat)
          strict <- (json \ "strict").validateOpt[Boolean]
        } yield AssistantTool.FunctionTool(name, description, parameters, strict)
      }
      val writes = Json.writes[AssistantTool.FunctionTool]
      Format(reads, writes)
    }
    lazy val assistantFileSearchToolFormat: Format[AssistantTool.FileSearchTool] = {
      implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
      Json.format[AssistantTool.FileSearchTool]
    }

    Format[AssistantTool](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap {
          case "code_interpreter" => JsSuccess(AssistantTool.CodeInterpreterTool)
          case "file_search" =>
            (json \ "file_search")
              .validate[AssistantTool.FileSearchTool](assistantFileSearchToolFormat)
          case "function" =>
            (json \ "function")
              .validate[AssistantTool.FunctionTool](assistantFunctionToolFormat)
          case _ => JsError("Unknown type")
        }
      },
      { (tool: AssistantTool) =>
        val typeField = Json.obj {
          val discriminatorValue = tool match {
            case AssistantTool.CodeInterpreterTool      => "code_interpreter"
            case AssistantTool.FileSearchTool(_)        => "file_search"
            case AssistantTool.FunctionTool(_, _, _, _) => "function"
          }
          typeDiscriminatorKey -> discriminatorValue
        }
        val customFields = tool match {
          case AssistantTool.CodeInterpreterTool => JsObject.empty
          case fileSearchTool: AssistantTool.FileSearchTool =>
            JsObject(
              Seq(
                "file_search" -> Json
                  .toJson(fileSearchTool)(assistantFileSearchToolFormat)
                  .as[JsObject]
              )
            )
          case functionTool: AssistantTool.FunctionTool =>
            JsObject(
              Seq(
                "function" -> Json
                  .toJson(functionTool)(assistantFunctionToolFormat)
                  .as[JsObject]
              )
            )
        }
        typeField ++ customFields
      }
    )
  }

  implicit val messageReads: Reads[BaseMessage] = Reads { (json: JsValue) =>
    val role = (json \ "role").as[ChatRole]
    (json \ "name").asOpt[String]

    val message: BaseMessage = role match {
      case ChatRole.System => json.as[SystemMessage]

      case ChatRole.Developer => json.as[DeveloperMessage]

      case ChatRole.User =>
        json.asOpt[UserMessage] match {
          case Some(userMessage) => userMessage
          case None              => json.as[UserSeqMessage]
        }

      case ChatRole.Tool => json.as[ToolMessage]

      case ChatRole.Assistant =>
        // if contains tool_calls, then it is AssistantToolMessage
        (json \ "tool_calls").asOpt[JsArray] match {
          case Some(_) => json.as[AssistantToolMessage]
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

      case m: DeveloperMessage => toJson(m)

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

  implicit lazy val chatCompletionToolWrites: Writes[ChatCompletionTool] =
    Writes[ChatCompletionTool] {
      _ match {
        case x: FunctionTool =>
          Json.obj("type" -> "function", "function" -> Json.toJson(x))
      }
    }

  implicit val chatCompletionResponseFormatTypeFormat
    : Format[ChatCompletionResponseFormatType] = enumFormat[ChatCompletionResponseFormatType](
    ChatCompletionResponseFormatType.json_object,
    ChatCompletionResponseFormatType.json_schema,
    ChatCompletionResponseFormatType.text
  )

  implicit val reasoningEffortFormat: Format[ReasoningEffort] = enumFormat[ReasoningEffort](
    ReasoningEffort.low,
    ReasoningEffort.medium,
    ReasoningEffort.high
  )

  implicit val serviceTierFormat: Format[ServiceTier] = enumFormat[ServiceTier](
    ServiceTier.auto,
    ServiceTier.default
  )

  implicit lazy val topLogprobInfoFormat: Format[TopLogprobInfo] = {
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
  implicit lazy val fineTuneErrorFormat: Format[Option[FineTuneError]] =
    new Format[Option[FineTuneError]] {
      def reads(json: JsValue): JsResult[Option[FineTuneError]] = json match {
        case JsObject(underlying) if underlying.isEmpty => JsSuccess(None)
        case JsNull                                     => JsSuccess(None)
        case _ => Json.reads[FineTuneError].reads(json).map(Some(_))
      }

      def writes(o: Option[FineTuneError]): JsValue = o match {
        case None        => JsObject.empty
        case Some(error) => Json.writes[FineTuneError].writes(error)
      }
    }

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

  implicit val fineTuneJobFormat: Format[FineTuneJob] = (
    (__ \ "id").format[String] and
      (__ \ "model").format[String] and
      (__ \ "created_at").format[ju.Date] and
      (__ \ "finished_at").formatNullable[ju.Date] and
      (__ \ "fine_tuned_model").formatNullable[String] and
      (__ \ "organization_id").format[String] and
      (__ \ "status").format[String] and
      (__ \ "training_file").format[String] and
      (__ \ "validation_file").formatNullable[String] and
      (__ \ "result_files").format[Seq[String]] and
      (__ \ "trained_tokens").formatNullable[Int] and
      (__ \ "error").format[Option[FineTuneError]](fineTuneErrorFormat) and
      (__ \ "hyperparameters").format[FineTuneHyperparams] and
      (__ \ "integrations").formatNullable[Seq[FineTune.Integration]] and
      (__ \ "seed").format[Int]
  )(
    FineTuneJob.apply,
    // somehow FineTuneJob.unapply is not working in Scala3
    (x: FineTuneJob) =>
      (
        x.id,
        x.model,
        x.created_at,
        x.finished_at,
        x.fine_tuned_model,
        x.organization_id,
        x.status,
        x.training_file,
        x.validation_file,
        x.result_files,
        x.trained_tokens,
        x.error,
        x.hyperparameters,
        x.integrations,
        x.seed
      )
  )

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
    // somehow ModerationCategories.unapply is not working in Scala3
    (x: ModerationCategories) =>
      (
        x.hate,
        x.hate_threatening,
        x.self_harm,
        x.sexual,
        x.sexual_minors,
        x.violence,
        x.violence_graphic
      )
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

  implicit lazy val assistantToolResourceCodeInterpreterResourceWrites
    : Writes[AssistantToolResource.CodeInterpreterResources] =
    Writes { c =>
      Json.obj("code_interpreter" -> Json.obj("file_ids" -> c.fileIds))
    }

  implicit lazy val assistantToolResourceFileSearchResourceWrites
    : Writes[AssistantToolResource.FileSearchResources] =
    Writes { f =>
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

  implicit lazy val assistantToolResourceWrites: Writes[AssistantToolResource] = Writes {
    case AssistantToolResource(Some(codeInterpreter), _) =>
      Json.toJson(codeInterpreter)(assistantToolResourceCodeInterpreterResourceWrites)
    case AssistantToolResource(_, Some(fileSearch)) =>
      Json.toJson(fileSearch)(assistantToolResourceFileSearchResourceWrites)
    case _ => Json.obj()
  }

  implicit lazy val codeInterpreterResourcesReads
    : Reads[AssistantToolResource.CodeInterpreterResources] = {
    implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
    Json.reads[AssistantToolResource.CodeInterpreterResources]
  }

  implicit lazy val fileSearchResourcesReads
    : Reads[AssistantToolResource.FileSearchResources] = {
    implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

    (
      (__ \ "vector_store_ids").readNullable[Seq[String]].map(_.getOrElse(Seq.empty)) and
        (__ \ "vector_stores")
          .readNullable[Seq[AssistantToolResource.VectorStore]]
          .map(_.getOrElse(Seq.empty))
    )(AssistantToolResource.FileSearchResources.apply _)
  }

  implicit lazy val assistantToolResourceReads: Reads[AssistantToolResource] = (
    (__ \ "code_interpreter").readNullable[AssistantToolResource.CodeInterpreterResources] and
      (__ \ "file_search").readNullable[AssistantToolResource.FileSearchResources]
  )(
    (
      codeInterpreter,
      fileSearch
    ) => AssistantToolResource(codeInterpreter, fileSearch)
  )

  implicit lazy val threadAndRunCodeInterpreterResourceWrites
    : Writes[ThreadAndRunToolResource.CodeInterpreterResource] = {
    implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
    Json.writes[ThreadAndRunToolResource.CodeInterpreterResource]
  }

  implicit lazy val threadAndRunCodeInterpreterResourceReads
    : Reads[ThreadAndRunToolResource.CodeInterpreterResource] = {
    implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
    Json.reads[ThreadAndRunToolResource.CodeInterpreterResource]
  }

  implicit lazy val threadAndRunFileSearchResourceWrites
    : Writes[ThreadAndRunToolResource.FileSearchResource] =
    Json.writes[ThreadAndRunToolResource.FileSearchResource]

  implicit lazy val threadAndRunFileSearchResourceReads
    : Reads[ThreadAndRunToolResource.FileSearchResource] =
    Json.reads[ThreadAndRunToolResource.FileSearchResource]

  implicit lazy val threadAndRunToolResourceWrites: Writes[ThreadAndRunToolResource] = {
    implicit val config: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
    Json.writes[ThreadAndRunToolResource]
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

  implicit val fileIdFormat: Format[FileId] = Format(
    Reads.StringReads.map(FileId.apply),
    Writes[FileId](fileId => JsString(fileId.file_id))
  )

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
        (__ \ "metadata").format[Map[String, String]] and
        (__ \ "chunking_strategy").formatNullable[ChunkingStrategy]
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
      (__ \ "tools").format[Seq[MessageAttachmentTool]]
  )(Attachment.apply, unlift(Attachment.unapply))

  implicit lazy val assistantReads: Reads[Assistant] = (
    (__ \ "id").read[String] and
      (__ \ "created_at").read[ju.Date] and
      (__ \ "name").readNullable[String] and
      (__ \ "description").readNullable[String] and
      (__ \ "model").read[String] and
      (__ \ "instructions").readNullable[String] and
      (__ \ "tools").read[Seq[AssistantTool]] and
      (__ \ "tool_resources")
        .read[Seq[AssistantToolResourceResponse]]
        .orElse(Reads.pure(Nil)) and
      (__ \ "metadata").read[Map[String, String]].orElse(Reads.pure(Map())) and
      (__ \ "temperature").readNullable[Double].orElse(Reads.pure(None)) and
      (__ \ "top_p").readNullable[Double].orElse(Reads.pure(None)) and
      (__ \ "response_format").read[ResponseFormat]
  )(Assistant.apply _)

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

  implicit lazy val functionToolFormat: Format[RunTool.FunctionTool] =
    Json.format[RunTool.FunctionTool]

  implicit lazy val runToolFormat: Format[RunTool] = {
    val runToolWrites: Writes[RunTool] = Writes {
      case RunTool.CodeInterpreterTool => Json.obj("type" -> "code_interpreter")
      case RunTool.FileSearchTool      => Json.obj("type" -> "file_search")
      case RunTool.FunctionTool(name) =>
        Json.obj("type" -> "function", "function" -> Json.obj("name" -> name))
    }

    val runToolReads: Reads[RunTool] = Reads { json =>
      (json \ "type").validate[String].flatMap {
        case "code_interpreter" => JsSuccess(RunTool.CodeInterpreterTool)
        case "file_search"      => JsSuccess(RunTool.FileSearchTool)
        case "function" =>
          (json \ "function" \ "name").validate[String].map(RunTool.FunctionTool.apply)
        case _ => JsError("Unknown type")
      }
    }

    Format(runToolReads, runToolWrites)
  }

//  implicit lazy val forcableToolFormat: Format[ForcableTool] = {
//    val reades: Reads[ForcableTool] = Reads { json =>
//      (json \ "type").validate[String].flatMap {
//        case "code_interpreter" => JsSuccess(CodeInterpreterSpec)
//        case "file_search"      => JsSuccess(FileSearchSpec)
//        case "function"         => json.validate[FunctionSpec]
//        case unsupportedType =>
//          JsError(s"Unsupported type of a forceable tool: $unsupportedType")
//      }
//    }
//
//    val writes: Writes[ForcableTool] = Writes {
//      case CodeInterpreterSpec => Json.obj("type" -> "code_interpreter")
//      case FileSearchSpec      => Json.obj("type" -> "file_search")
//      case functionTool: FunctionSpec =>
//        Json.toJson(functionTool).as[JsObject] + ("type" -> JsString("function"))
//    }
//
//    Format(reades, writes)
//  }

  implicit val toolChoiceFormat: Format[ToolChoice] = {
    import ToolChoice._

    val reads: Reads[ToolChoice] = Reads { json =>
      json.validate[String].flatMap {
        case "none"     => JsSuccess(None)
        case "auto"     => JsSuccess(Auto)
        case "required" => JsSuccess(Required)
        case _          => runToolFormat.reads(json).map(EnforcedTool.apply)
      }
    }

    val writes: Writes[ToolChoice] = Writes {
      case None                  => JsString("none")
      case Auto                  => JsString("auto")
      case Required              => JsString("required")
      case EnforcedTool(runTool) => runToolFormat.writes(runTool)
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

  implicit lazy val truncationStrategyWrites: Writes[TruncationStrategy] =
    Json.format[TruncationStrategy]

  implicit lazy val threadWrites: Writes[Thread] = Json.writes[Thread]

  implicit lazy val threadAndRunRoleWrites: Writes[ThreadAndRunRole] =
    Writes {
      case ChatRole.Assistant => JsString("assistant")
      case ChatRole.User      => JsString("user")
    }

  implicit lazy val theadAndRunImageFileDetailsWrites
    : Writes[ThreadAndRun.Content.ContentBlock.ImageFileDetail] = Writes {
    case ImageDetail.Low  => JsString("low")
    case ImageDetail.High => JsString("high")
  }

  implicit lazy val threadAndRunContentBlockWrites
    : Writes[ThreadAndRun.Content.ContentBlock] = {
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Writes {
      case ThreadAndRun.Content.ContentBlock.TextBlock(text) =>
        Json.toJson("type" -> "text", "text" -> text)
      case ThreadAndRun.Content.ContentBlock.ImageFileBlock(fileId, detail) =>
        Json.toJson(
          "type" -> "image_file",
          "image_file" -> Json.obj("file_id" -> fileId, "detail" -> Json.toJson(detail))
        )
    }
  }

  implicit lazy val threadAndRunContentWrites: Writes[ThreadAndRun.Content] = Writes {
    case ThreadAndRun.Content.SingleString(text)  => JsString(text)
    case ThreadAndRun.Content.ContentBlocks(tool) => JsArray(tool.map(Json.toJson(_)))
  }

  implicit lazy val theadAndRunMessageWrites: Writes[ThreadAndRun.Message] = {
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Writes { case message =>
      Json.obj(
        "content" -> Json.toJson(message.content),
        "role" -> Json.toJson(message.role),
        "attachments" -> Json.toJson(message.attachments),
        "metadata" -> Json.toJson(message.metadata)
      )
    }
  }

  implicit lazy val threadAndRunWrites: Writes[ThreadAndRun] = {
    // snake case naming strategy
    implicit val config: JsonConfiguration = JsonConfiguration(SnakeCase)
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Json.writes[ThreadAndRun]
  }

  implicit lazy val jsonTypeFormat: Format[JsonType] = enumFormat[JsonType](
    JsonType.Object,
    JsonType.String,
    JsonType.Number,
    JsonType.Boolean,
    JsonType.Null,
    JsonType.Array
  )

  implicit lazy val jsonSchemaWrites: Writes[JsonSchema] = {
    implicit val stringWrites = Json.writes[JsonSchema.String]
    implicit val numberWrites = Json.writes[JsonSchema.Number]
    implicit val booleanWrites = Json.writes[JsonSchema.Boolean]
    //    implicit val nullWrites = Json.writes[JsonSchema.Null]

    def writesAux(o: JsonSchema): JsValue = {
      val typeValueJson = o.`type`.toString

      val json: JsObject = o match {
        case c: JsonSchema.String =>
          val json = Json.toJson(c).as[JsObject]
          if ((json \ "enum").asOpt[Seq[String]].exists(_.isEmpty)) json - "enum" else json

        case c: JsonSchema.Number =>
          Json.toJson(c).as[JsObject]

        case c: JsonSchema.Boolean =>
          Json.toJson(c).as[JsObject]

        case _: JsonSchema.Null =>
          Json.obj()

        case c: JsonSchema.Object =>
          Json.obj(
            "properties" -> JsObject(
              c.properties.map { case (key, value) => (key, writesAux(value)) }
            ),
            "required" -> c.required
          )

        case c: JsonSchema.Array =>
          Json.obj(
            "items" -> writesAux(c.items)
          )
      }

      json ++ Json.obj("type" -> typeValueJson)
    }

    (o: JsonSchema) => writesAux(o)
  }

  implicit lazy val jsonSchemaReads: Reads[JsonSchema] = new Reads[JsonSchema] {
    implicit val stringReads: Reads[JsonSchema.String] = (
      (__ \ "description").readNullable[String] and
        (__ \ "enum").readWithDefault[Seq[String]](Nil)
    )(JsonSchema.String.apply _)

    implicit val numberReads: Reads[JsonSchema.Number] = Json.reads[JsonSchema.Number]
    implicit val booleanReads: Reads[JsonSchema.Boolean] = Json.reads[JsonSchema.Boolean]
    //    implicit val nullReads = Json.reads[JsonSchema.Null]

    def readsAux(o: JsValue): JsResult[JsonSchema] = {
      (o \ "type")
        .asOpt[JsonType]
        .map {
          case JsonType.String =>
            Json.fromJson[JsonSchema.String](o)

          case JsonType.Number =>
            Json.fromJson[JsonSchema.Number](o)

          case JsonType.Boolean =>
            Json.fromJson[JsonSchema.Boolean](o)

          case JsonType.Null =>
            JsSuccess(JsonSchema.Null())

          case JsonType.Object =>
            (o \ "properties")
              .asOpt[JsObject]
              .map { propertiesJson =>
                val propertiesResults = propertiesJson.fields.map { case (key, jsValue) =>
                  (key, readsAux(jsValue))
                }.toMap

                val propertiesErrors = propertiesResults.collect { case (_, JsError(errors)) =>
                  errors
                }
                val properties = propertiesResults.collect { case (key, JsSuccess(value, _)) =>
                  (key, value)
                }

                val required = (o \ "required").asOpt[Seq[String]].getOrElse(Nil)

                if (propertiesErrors.isEmpty)
                  JsSuccess(JsonSchema.Object(properties, required))
                else
                  JsError(propertiesErrors.reduce(_ ++ _))
              }
              .getOrElse(
                JsError("Object schema must have a 'properties' field.")
              )

          case JsonType.Array =>
            (o \ "items")
              .asOpt[JsObject]
              .map { itemsJson =>
                readsAux(itemsJson).map { items =>
                  JsonSchema.Array(items)
                }
              }
              .getOrElse(
                JsError("Array schema must have an 'items' field.")
              )
        }
        .getOrElse(
          JsError("Schema must have a 'type' field.")
        )
    }

    override def reads(json: JsValue): JsResult[JsonSchema] = readsAux(json)
  }

  implicit lazy val jsonSchemaFormat: Format[JsonSchema] =
    Format(jsonSchemaReads, jsonSchemaWrites)

  implicit lazy val eitherJsonSchemaReads: Reads[Either[JsonSchema, Map[String, Any]]] = {
    implicit val stringAnyMapFormat: Format[Map[String, Any]] =
      JsonUtil.StringAnyMapFormat

    Reads[Either[JsonSchema, Map[String, Any]]] { (json: JsValue) =>
      json
        .validate[JsonSchema]
        .map(Left(_))
        .orElse(
          json.validate[Map[String, Any]].map(Right(_))
        )
    }
  }

  implicit lazy val eitherJsonSchemaWrites: Writes[Either[JsonSchema, Map[String, Any]]] = {
    implicit val stringAnyMapFormat: Format[Map[String, Any]] =
      JsonUtil.StringAnyMapFormat

    Writes[Either[JsonSchema, Map[String, Any]]] {
      case Left(schema) => Json.toJson(schema)
      case Right(map)   => Json.toJson(map)
    }
  }

  implicit lazy val eitherJsonSchemaFormat: Format[Either[JsonSchema, Map[String, Any]]] =
    Format(eitherJsonSchemaReads, eitherJsonSchemaWrites)

  implicit val jsonSchemaDefFormat: Format[JsonSchemaDef] = Json.format[JsonSchemaDef]
}
