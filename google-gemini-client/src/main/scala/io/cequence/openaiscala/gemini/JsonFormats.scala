package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.gemini.domain.response._
import io.cequence.openaiscala.gemini.domain.settings.SpeechConfig.VoiceConfig
import io.cequence.openaiscala.gemini.domain.settings._
import io.cequence.openaiscala.gemini.domain.settings.ToolConfig.FunctionCallingConfig
import io.cequence.openaiscala.gemini.domain.{
  ChatRole,
  Content,
  DynamicRetrievalConfig,
  DynamicRetrievalPredictorMode,
  FunctionDeclaration,
  HarmBlockThreshold,
  HarmCategory,
  HarmProbability,
  Modality,
  Model,
  Part,
  PartPrefix,
  Schema,
  SchemaType,
  Tool,
  ToolPrefix
}
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.JsonUtil.enumFormat
import play.api.libs.functional.syntax._
import play.api.libs.json._

object JsonFormats extends JsonFormats

trait JsonFormats {

  // Content and Parts
  implicit val chatRoleFormat: Format[ChatRole] = enumFormat(ChatRole.values: _*)

  private implicit val textPartFormat: Format[Part.TextPart] = Json.format[Part.TextPart]
  private implicit val inlineDataPartFormat: Format[Part.InlineDataPart] =
    Json.format[Part.InlineDataPart]

  private implicit val functionCallPartFormat: Format[Part.FunctionCallPart] = {
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Json.format[Part.FunctionCallPart]
  }

  private implicit val functionResponsePartFormat: Format[Part.FunctionResponsePart] = {
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Json.format[Part.FunctionResponsePart]
  }

  private implicit val fileDataPartFormat: Format[Part.FileDataPart] =
    Json.format[Part.FileDataPart]
  private implicit val executableCodePartFormat: Format[Part.ExecutableCodePart] =
    Json.format[Part.ExecutableCodePart]
  private implicit val codeExecutionResultPartFormat: Format[Part.CodeExecutionResultPart] =
    Json.format[Part.CodeExecutionResultPart]

  implicit val partWrites: Writes[Part] = Writes[Part] { part: Part =>
    val prefix = part.prefix.toString()

    def toJsonWithPrefix[T: Format](p: T) = {
      val json = Json.toJson(p)
      Json.obj(prefix -> json)
    }

    part match {
      case p: Part.TextPart                => Json.toJson(p) // no prefix
      case p: Part.InlineDataPart          => toJsonWithPrefix(p)
      case p: Part.FunctionCallPart        => toJsonWithPrefix(p)
      case p: Part.FunctionResponsePart    => toJsonWithPrefix(p)
      case p: Part.FileDataPart            => toJsonWithPrefix(p)
      case p: Part.ExecutableCodePart      => toJsonWithPrefix(p)
      case p: Part.CodeExecutionResultPart => toJsonWithPrefix(p)
    }
  }

  implicit val partReads: Reads[Part] = { json: JsValue =>
    json.validate[JsObject].map { jsonObject =>
      assert(jsonObject.fields.size == 1)
      val (prefixFieldName, prefixJson) = jsonObject.fields.head

      PartPrefix.of(prefixFieldName) match {
        case PartPrefix.text                => json.as[Part.TextPart]
        case PartPrefix.inlineData          => prefixJson.as[Part.InlineDataPart]
        case PartPrefix.functionCall        => prefixJson.as[Part.FunctionCallPart]
        case PartPrefix.functionResponse    => prefixJson.as[Part.FunctionResponsePart]
        case PartPrefix.fileData            => prefixJson.as[Part.FileDataPart]
        case PartPrefix.executableCode      => prefixJson.as[Part.ExecutableCodePart]
        case PartPrefix.codeExecutionResult => prefixJson.as[Part.CodeExecutionResultPart]
        case _ => throw new OpenAIScalaClientException(s"Unknown part type: $prefixFieldName")
      }
    }
  }

  implicit val partFormat: Format[Part] = Format(partReads, partWrites)

  implicit val contentFormat: Format[Content] = Json.format[Content]

  // Tools
  implicit val toolPrefixFormat: Format[ToolPrefix] = enumFormat(ToolPrefix.values: _*)
  implicit val dynamicRetrievalPredictorModeFormat: Format[DynamicRetrievalPredictorMode] =
    enumFormat(DynamicRetrievalPredictorMode.values: _*)
  implicit val schemaTypeFormat: Format[SchemaType] = enumFormat(SchemaType.values: _*)

  implicit val dynamicRetrievalConfigFormat: Format[DynamicRetrievalConfig] =
    Json.format[DynamicRetrievalConfig]
  implicit val schemaFormat: Format[Schema] = Json.format[Schema]

  private implicit val functionDeclarationFormat: Format[FunctionDeclaration] =
    Json.format[FunctionDeclaration]
  private implicit val functionDeclarationsFormat: Format[Tool.FunctionDeclarations] =
    Json.format[Tool.FunctionDeclarations]
  private implicit val googleSearchRetrievalFormat: Format[Tool.GoogleSearchRetrieval] =
    Json.format[Tool.GoogleSearchRetrieval]

  implicit val toolWrites: Writes[Tool] = Writes[Tool] { part: Tool =>
    val prefix = part.prefix.toString()

    def toJsonWithPrefix(json: JsValue) = Json.obj(prefix -> json)

    part match {
      case p: Tool.FunctionDeclarations  => Json.toJson(p) // no prefix
      case p: Tool.GoogleSearchRetrieval => toJsonWithPrefix(Json.toJson(p))
      case Tool.CodeExecution            => toJsonWithPrefix(Json.obj()) // empty object
      case Tool.GoogleSearch             => toJsonWithPrefix(Json.obj()) // empty object
    }
  }

  implicit val toolReads: Reads[Tool] = { json: JsValue =>
    json.validate[JsObject].map { jsonObject =>
      assert(jsonObject.fields.size == 1, s"Expected exactly one field in $json")
      val (prefixFieldName, prefixJson) = jsonObject.fields.head

      ToolPrefix.of(prefixFieldName) match {
        case ToolPrefix.functionDeclarations  => json.as[Tool.FunctionDeclarations]
        case ToolPrefix.googleSearchRetrieval => prefixJson.as[Tool.GoogleSearchRetrieval]
        case ToolPrefix.codeExecution         => Tool.CodeExecution // no fields
        case ToolPrefix.googleSearch          => Tool.GoogleSearch // no fields
        case _ => throw new OpenAIScalaClientException(s"Unknown tool type: $prefixFieldName")
      }
    }
  }

  implicit val toolFormat: Format[Tool] = Format(toolReads, toolWrites)

  implicit val functionCallingModeFormat: Format[FunctionCallingMode] = enumFormat(
    FunctionCallingMode.values: _*
  )

  private implicit val functionCallingConfigFormat: Format[FunctionCallingConfig] =
    Json.format[FunctionCallingConfig]

  implicit val toolConfigWrites: Writes[ToolConfig] = Writes[ToolConfig] {
    case p: ToolConfig.FunctionCallingConfig =>
      Json.obj("functionCallingConfig" -> Json.toJson(p))
  }

  implicit val toolConfigReads: Reads[ToolConfig] = { json: JsValue =>
    json.validate[JsObject].map { jsonObject =>
      assert(jsonObject.fields.size == 1, s"Expected exactly one field in $json")
      val (prefixFieldName, prefixJson) = jsonObject.fields.head

      prefixFieldName match {
        case "functionCallingConfig" => prefixJson.as[ToolConfig.FunctionCallingConfig]
        case _ =>
          throw new OpenAIScalaClientException(s"Unknown tool config type: $prefixFieldName")
      }
    }
  }

  implicit val toolConfigFormat: Format[ToolConfig] = Format(toolConfigReads, toolConfigWrites)

  // Safety
  implicit lazy val harmCategoryFormat: Format[HarmCategory] = enumFormat(
    HarmCategory.values: _*
  )
  implicit lazy val harmBlockThresholdFormat: Format[HarmBlockThreshold] = enumFormat(
    HarmBlockThreshold.values: _*
  )
  implicit lazy val harmProbabilityFormat: Format[HarmProbability] = enumFormat(
    HarmProbability.values: _*
  )

  implicit lazy val safetySettingFormat: Format[SafetySetting] = (
    (__ \ "harmCategory").format[HarmCategory] and
      (__ \ "harmBlockThreshold").format[HarmBlockThreshold]
  )(SafetySetting.apply, unlift(SafetySetting.unapply))

  // Generation config
  implicit val prebuiltVoiceConfigFormat: Format[PrebuiltVoiceConfig] =
    Json.format[PrebuiltVoiceConfig]

  private implicit val voiceConfigFormat: Format[VoiceConfig] = Json.format[VoiceConfig]

  implicit val speechConfigWrites: Writes[SpeechConfig] = Writes[SpeechConfig] {
    case p: SpeechConfig.VoiceConfig => Json.obj("voiceConfig" -> Json.toJson(p))
  }

  implicit val speechConfigReads: Reads[SpeechConfig] = { json: JsValue =>
    json.validate[JsObject].map { jsonObject =>
      assert(jsonObject.fields.size == 1, s"Expected exactly one field in $json")
      val (prefixFieldName, prefixJson) = jsonObject.fields.head

      prefixFieldName match {
        case "voiceConfig" => prefixJson.as[SpeechConfig.VoiceConfig]
        case _ =>
          throw new OpenAIScalaClientException(s"Unknown speech config type: $prefixFieldName")
      }
    }
  }

  implicit val speechConfigFormat: Format[SpeechConfig] =
    Format(speechConfigReads, speechConfigWrites)

  implicit val modalityFormat: Format[Modality] = enumFormat(Modality.values: _*)
  implicit val generationConfigFormat: Format[GenerationConfig] = Json.format[GenerationConfig]

  // Grounding Attribution and Metadata
  implicit val retrievalMetadataFormat: Format[RetrievalMetadata] =
    Json.format[RetrievalMetadata]
  implicit val searchEntryPointFormat: Format[SearchEntryPoint] = Json.format[SearchEntryPoint]
  implicit val segmentFormat: Format[Segment] = Json.format[Segment]
  implicit val groundingSupportFormat: Format[GroundingSupport] =
    Json.using[Json.WithDefaultValues].format[GroundingSupport]
  implicit val webFormat: Format[Web] = Json.format[Web]
  implicit val groundingChunkFormat: Format[GroundingChunk] = Json.format[GroundingChunk]
  implicit val groundingMetadataFormat: Format[GroundingMetadata] =
    Json.using[Json.WithDefaultValues].format[GroundingMetadata]

  private implicit val semanticRetrieverChunkFormat
    : Format[AttributionSourceId.SemanticRetrieverChunk] =
    Json.format[AttributionSourceId.SemanticRetrieverChunk]
  private implicit val groundingPassageIdFormat
    : Format[AttributionSourceId.GroundingPassageId] =
    Json.format[AttributionSourceId.GroundingPassageId]

  implicit val attributionSourceIdWrites: Writes[AttributionSourceId] =
    Writes[AttributionSourceId] { sourceId: AttributionSourceId =>
      val prefix = sourceId.prefix.toString()

      def toJsonWithPrefix[T: Format](item: T) = Json.obj(prefix -> Json.toJson(item))

      sourceId match {
        case p: AttributionSourceId.GroundingPassageId     => toJsonWithPrefix(p)
        case p: AttributionSourceId.SemanticRetrieverChunk => toJsonWithPrefix(p)
      }
    }

  implicit val attributionSourceIdReads: Reads[AttributionSourceId] = { json: JsValue =>
    json.validate[JsObject].map { jsonObject =>
      assert(jsonObject.fields.size == 1, s"Expected exactly one field in $json")
      val (prefixFieldName, prefixJson) = jsonObject.fields.head

      AttributionSourceIdPrefix.of(prefixFieldName) match {
        case AttributionSourceIdPrefix.groundingPassage =>
          prefixJson.as[AttributionSourceId.GroundingPassageId]

        case AttributionSourceIdPrefix.semanticRetrieverChunk =>
          prefixJson.as[AttributionSourceId.SemanticRetrieverChunk]

        case _ =>
          throw new OpenAIScalaClientException(
            s"Unknown attribution source id type: $prefixFieldName"
          )
      }
    }
  }
  implicit val attributionSourceIdFormat: Format[AttributionSourceId] =
    Format(attributionSourceIdReads, attributionSourceIdWrites)

  implicit val groundingAttributionFormat: Format[GroundingAttribution] =
    Json.format[GroundingAttribution]

  // Candidate and Generate Content Response
  implicit val finishReasonFormat: Format[FinishReason] = enumFormat(FinishReason.values: _*)
  implicit val blockReasonFormat: Format[BlockReason] = enumFormat(BlockReason.values: _*)
  implicit val safetyRatingFormat: Format[SafetyRating] = Json.format[SafetyRating]
  implicit val citationSourceFormat: Format[CitationSource] = Json.format[CitationSource]
  implicit val citationMetadataFormat: Format[CitationMetadata] =
    Json.using[Json.WithDefaultValues].format[CitationMetadata]
  implicit val modalityTokenCountFormat: Format[ModalityTokenCount] =
    Json.format[ModalityTokenCount]
  implicit val usageMetadataFormat: Format[UsageMetadata] =
    Json.using[Json.WithDefaultValues].format[UsageMetadata]
  implicit val candidateFormat: Format[Candidate] =
    Json.using[Json.WithDefaultValues].format[Candidate]
  implicit val topCandidatesFormat: Format[TopCandidates] = Json.format[TopCandidates]

  implicit val promptFeedbackFormat: Format[PromptFeedback] = Json.format[PromptFeedback]
  implicit val generateContentResponseFormat: Format[GenerateContentResponse] =
    Json.using[Json.WithDefaultValues].format[GenerateContentResponse]

  // implicit val logprobsResultFormat: Format[LogprobsResult] = Json.format[LogprobsResult]

  // Model
  implicit val modelFormat: Format[Model] = Json.using[Json.WithDefaultValues].format[Model]
  implicit val listModelsFormat: Format[ListModelsResponse] = Json.format[ListModelsResponse]
}
