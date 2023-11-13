package io.cequence.openaiscala

import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.domain._

import java.{util => ju}
import io.cequence.openaiscala.domain.response._
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, _}

object JsonFormats {
  private implicit val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  implicit val PermissionFormat: Format[Permission] = Json.format[Permission]
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

  implicit object ChatRoleFormat extends Format[ChatRole] {
    override def reads(json: JsValue): JsResult[ChatRole] = {
      json.asSafe[String] match {
        case "user"      => JsSuccess(ChatRole.User)
        case "assistant" => JsSuccess(ChatRole.Assistant)
        case "system"    => JsSuccess(ChatRole.System)
        case "function"  => JsSuccess(ChatRole.Function)
        case x           => JsError(s"$x is not a valid message role.")
      }
    }

    override def writes(o: ChatRole): JsValue = {
      JsString(o.toString.toLowerCase())
    }
  }

  implicit val functionCallSpecFormat: Format[FunctionCallSpec] =
    Json.format[FunctionCallSpec]
  implicit val messageSpecFormat: Format[MessageSpec] = Json.format[MessageSpec]
  implicit val funMessageSpecFormat: Format[FunMessageSpec] =
    Json.format[FunMessageSpec]

  implicit val functionSpecFormat: Format[FunctionSpec] = {
    // use just here for FunctionSpec
    implicit val stringAnyMapFormat: Format[Map[String, Any]] = JsonUtil.StringAnyMapFormat
    Json.format[FunctionSpec]
  }

  implicit val chatCompletionChoiceInfoFormat: Format[ChatCompletionChoiceInfo] =
    Json.format[ChatCompletionChoiceInfo]
  implicit val chatCompletionResponseFormat: Format[ChatCompletionResponse] =
    Json.format[ChatCompletionResponse]

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
}
