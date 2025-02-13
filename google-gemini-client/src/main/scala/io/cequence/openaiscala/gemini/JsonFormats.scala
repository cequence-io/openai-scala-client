package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.gemini.domain.Expiration.{ExpireTime, TTL}
import io.cequence.openaiscala.gemini.domain.response._
import io.cequence.openaiscala.gemini.domain.settings.SpeechConfig.VoiceConfig
import io.cequence.openaiscala.gemini.domain.settings.ToolConfig.FunctionCallingConfig
import io.cequence.openaiscala.gemini.domain.settings._
import io.cequence.openaiscala.gemini.domain._
import io.cequence.wsclient.JsonUtil
import io.cequence.wsclient.JsonUtil.enumFormat
import play.api.libs.functional.syntax._
import play.api.libs.json._

object JsonFormats extends JsonFormats

trait JsonFormats {

  // Content and Parts
  implicit val chatRoleFormat: Format[ChatRole] = enumFormat(ChatRole.values: _*)

  private implicit val textPartFormat: Format[Part.Text] = Json.format[Part.Text]
  private implicit val inlineDataPartFormat: Format[Part.InlineData] =
    Json.format[Part.InlineData]

  private implicit val functionCallPartFormat: Format[Part.FunctionCall] = {
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Json.format[Part.FunctionCall]
  }

  private implicit val functionResponsePartFormat: Format[Part.FunctionResponse] = {
    implicit val mapFormat = JsonUtil.StringAnyMapFormat
    Json.format[Part.FunctionResponse]
  }

  private implicit val fileDataPartFormat: Format[Part.FileData] =
    Json.format[Part.FileData]
  private implicit val executableCodePartFormat: Format[Part.ExecutableCode] =
    Json.format[Part.ExecutableCode]
  private implicit val codeExecutionResultPartFormat: Format[Part.CodeExecutionResult] =
    Json.format[Part.CodeExecutionResult]

  implicit val partWrites: Writes[Part] = Writes[Part] { (part: Part) =>
    val prefix = part.prefix.toString()

    def toJsonWithPrefix[T: Format](p: T) = {
      val json = Json.toJson(p)
      Json.obj(prefix -> json)
    }

    part match {
      case p: Part.Text                => Json.toJson(p) // no prefix
      case p: Part.InlineData          => toJsonWithPrefix(p)
      case p: Part.FunctionCall        => toJsonWithPrefix(p)
      case p: Part.FunctionResponse    => toJsonWithPrefix(p)
      case p: Part.FileData            => toJsonWithPrefix(p)
      case p: Part.ExecutableCode      => toJsonWithPrefix(p)
      case p: Part.CodeExecutionResult => toJsonWithPrefix(p)
    }
  }

  implicit val partReads: Reads[Part] = { (json: JsValue) =>
    json.validate[JsObject].map { (jsonObject: JsObject) =>
      assert(jsonObject.fields.size == 1)
      val (prefixFieldName, prefixJson) = jsonObject.fields.head

      PartPrefix.of(prefixFieldName) match {
        case PartPrefix.text                => json.as[Part.Text]
        case PartPrefix.inlineData          => prefixJson.as[Part.InlineData]
        case PartPrefix.functionCall        => prefixJson.as[Part.FunctionCall]
        case PartPrefix.functionResponse    => prefixJson.as[Part.FunctionResponse]
        case PartPrefix.fileData            => prefixJson.as[Part.FileData]
        case PartPrefix.executableCode      => prefixJson.as[Part.ExecutableCode]
        case PartPrefix.codeExecutionResult => prefixJson.as[Part.CodeExecutionResult]
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

  implicit val toolWrites: Writes[Tool] = Writes[Tool] { (part: Tool) =>
    val prefix = part.prefix.toString()

    def toJsonWithPrefix(json: JsValue) = Json.obj(prefix -> json)

    part match {
      case p: Tool.FunctionDeclarations  => Json.toJson(p) // no prefix
      case p: Tool.GoogleSearchRetrieval => toJsonWithPrefix(Json.toJson(p))
      case Tool.CodeExecution            => toJsonWithPrefix(Json.obj()) // empty object
      case Tool.GoogleSearch             => toJsonWithPrefix(Json.obj()) // empty object
    }
  }

  implicit val toolReads: Reads[Tool] = { (json: JsValue) =>
    json.validate[JsObject].map { (jsonObject: JsObject) =>
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

  implicit val toolConfigReads: Reads[ToolConfig] = { (json: JsValue) =>
    json.validate[JsObject].map { (jsonObject: JsObject) =>
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
    (__ \ "category").format[HarmCategory] and
      (__ \ "threshold").format[HarmBlockThreshold]
  )(
    SafetySetting.apply,
    // somehow unlift(SafetySetting.unapply) is not working in Scala3
    (x: SafetySetting) =>
      (
        x.category,
        x.threshold
      )
  )

  // Generation config
  implicit val prebuiltVoiceConfigFormat: Format[PrebuiltVoiceConfig] =
    Json.format[PrebuiltVoiceConfig]

  private implicit val voiceConfigFormat: Format[VoiceConfig] = Json.format[VoiceConfig]

  implicit val speechConfigWrites: Writes[SpeechConfig] = Writes[SpeechConfig] {
    case p: SpeechConfig.VoiceConfig => Json.obj("voiceConfig" -> Json.toJson(p))
  }

  implicit val speechConfigReads: Reads[SpeechConfig] = { (json: JsValue) =>
    json.validate[JsObject].map { (jsonObject: JsObject) =>
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
    Writes[AttributionSourceId] { (sourceId: AttributionSourceId) =>
      val prefix = sourceId.prefix.toString()

      def toJsonWithPrefix[T: Format](item: T) = Json.obj(prefix -> Json.toJson(item))

      sourceId match {
        case p: AttributionSourceId.GroundingPassageId     => toJsonWithPrefix(p)
        case p: AttributionSourceId.SemanticRetrieverChunk => toJsonWithPrefix(p)
      }
    }

  implicit val attributionSourceIdReads: Reads[AttributionSourceId] = { (json: JsValue) =>
    json.validate[JsObject].map { (jsonObject: JsObject) =>
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

//  implicit lazy val candidateFormat: Format[Candidate] =
//    Json.using[Json.WithDefaultValues].format[Candidate]

  implicit lazy val candidateWrites: Writes[Candidate] = (
    (__ \ "content").write[Content] and
      (__ \ "finishReason").writeNullable[FinishReason] and
      (__ \ "safetyRatings").write[Seq[SafetyRating]] and
      (__ \ "citationMetadata").writeNullable[CitationMetadata] and
      (__ \ "tokenCount").writeNullable[Int] and
      (__ \ "groundingAttributions").write[Seq[GroundingAttribution]] and
      (__ \ "groundingMetadata").writeNullable[GroundingMetadata] and
      (__ \ "avgLogprobs").writeNullable[Double] and
      (__ \ "logprobsResult").lazyWriteNullable[LogprobsResult](logprobsResultWrites) and
      (__ \ "index").formatNullable[Int]
  )(
    // somehow unlift(Candidate.unapply) is not working in Scala3
    (x: Candidate) =>
      (
        x.content,
        x.finishReason,
        x.safetyRatings,
        x.citationMetadata,
        x.tokenCount,
        x.groundingAttributions,
        x.groundingMetadata,
        x.avgLogprobs,
        x.logprobsResult,
        x.index
      )
  )

  implicit lazy val candidateReads: Reads[Candidate] = (
    (__ \ "content").read[Content] and
      (__ \ "finishReason").readNullable[FinishReason] and
      (__ \ "safetyRatings").readWithDefault[Seq[SafetyRating]](Nil) and
      (__ \ "citationMetadata").readNullable[CitationMetadata] and
      (__ \ "tokenCount").readNullable[Int] and
      (__ \ "groundingAttributions").readWithDefault[Seq[GroundingAttribution]](Nil) and
      (__ \ "groundingMetadata").readNullable[GroundingMetadata] and
      (__ \ "avgLogprobs").readNullable[Double] and
      (__ \ "logprobsResult").lazyReadNullable[LogprobsResult](logprobsResultReads) and
      (__ \ "index").readNullable[Int]
  )(Candidate.apply _)

  implicit lazy val candidateFormat: Format[Candidate] =
    Format(candidateReads, candidateWrites)

  implicit lazy val logprobsResultWrites: Writes[LogprobsResult] = (
    (__ \ "topCandidates").write[Seq[TopCandidates]] and
      (__ \ "chosenCandidates").write[Seq[Candidate]]
  )(
    // somehow unlift(LogprobsResult.unapply) is not working in Scala3
    (x: LogprobsResult) =>
      (
        x.topCandidates,
        x.chosenCandidates
      )
  )

  implicit lazy val logprobsResultReads: Reads[LogprobsResult] = (
    (__ \ "topCandidates").readWithDefault[Seq[TopCandidates]](Nil) and
      (__ \ "chosenCandidates").readWithDefault[Seq[Candidate]](Nil)
  )(LogprobsResult.apply _)

  implicit lazy val logprobsResultFormat: Format[LogprobsResult] =
    Format(logprobsResultReads, logprobsResultWrites)

  implicit lazy val topCandidatesFormat: Format[TopCandidates] = Json.format[TopCandidates]

  implicit val promptFeedbackFormat: Format[PromptFeedback] = Json.format[PromptFeedback]
  implicit val generateContentResponseFormat: Format[GenerateContentResponse] =
    Json.using[Json.WithDefaultValues].format[GenerateContentResponse]

  // Model
  implicit val modelFormat: Format[Model] = Json.using[Json.WithDefaultValues].format[Model]
  implicit val listModelsFormat: Format[ListModelsResponse] = Json.format[ListModelsResponse]

  private val modelsPrefix = "models/"

  implicit val cachedContentWrites: Writes[CachedContent] = (
    (__ \ "contents").write[Seq[Content]] and
      (__ \ "tools").write[Seq[Tool]] and
      (__ \ "expireTime").writeNullable[String] and
      (__ \ "ttl").writeNullable[String] and
      (__ \ "name").writeNullable[String] and
      (__ \ "displayName").writeNullable[String] and
      (__ \ "model").write[String] and
      (__ \ "systemInstruction").writeNullable[Content] and
      (__ \ "toolConfig").writeNullable[ToolConfig]
  )(cachedContent =>
    (
      cachedContent.contents,
      cachedContent.tools,
      cachedContent.expireTime match {
        case e: Expiration.ExpireTime => Some(e.value)
        case _                        => None
      },
      cachedContent.expireTime match {
        case e: Expiration.TTL => Some(e.value)
        case _                 => None
      },
      cachedContent.name,
      cachedContent.displayName,
      if (cachedContent.model.startsWith(modelsPrefix)) {
        cachedContent.model
      } else {
        s"${modelsPrefix}${cachedContent.model}"
      },
      cachedContent.systemInstruction,
      cachedContent.toolConfig
    )
  )

  implicit val cachedContentReads: Reads[CachedContent] = (
    (__ \ "contents").readWithDefault[Seq[Content]](Nil) and
      (__ \ "tools").readWithDefault[Seq[Tool]](Nil) and
      (__ \ "expireTime").readNullable[String] and
      (__ \ "ttl").readNullable[String] and
      (__ \ "name").readNullable[String] and
      (__ \ "displayName").readNullable[String] and
      (__ \ "model").read[String] and
      (__ \ "systemInstruction").readNullable[Content] and
      (__ \ "toolConfig").readNullable[ToolConfig]
  )(
    (
      contents,
      tools,
      expireTime,
      ttl,
      name,
      displayName,
      model,
      systemInstruction,
      toolConfig
    ) =>
      CachedContent(
        contents = contents,
        tools = tools,
        expireTime = expireTime
          .map(ExpireTime(_))
          .orElse(ttl.map(TTL(_)))
          .getOrElse(
            throw new OpenAIScalaClientException("Either expireTime or ttl must be provided.")
          ),
        name = name,
        displayName = displayName,
        model = model.stripPrefix(modelsPrefix),
        systemInstruction = systemInstruction,
        toolConfig = toolConfig
      )
  )

  implicit val cachedContentFormat: Format[CachedContent] =
    Format(cachedContentReads, cachedContentWrites)

  implicit val listCachedContentsResponseFormat: Format[ListCachedContentsResponse] =
    Json.using[Json.WithDefaultValues].format[ListCachedContentsResponse]
}
