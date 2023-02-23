package io.cequence.openaiscala

import java.{util => ju}
import io.cequence.openaiscala.domain.response._
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, _}

object JsonFormats {
  private implicit val dateFormat: Format[ju.Date] = JsonUtil.SecDateFormat

  implicit val PermissionFormat: Format[Permission] = Json.format[Permission]
  implicit val modelSpecFormat: Format[ModelInfo] = Json.format[ModelInfo]

  implicit val usageInfoFormat: Format[UsageInfo] = Json.format[UsageInfo]

  private implicit val stringDoubleMapFormat: Format[Map[String, Double]] = JsonUtil.StringDoubleMapFormat
  private implicit val stringStringMapFormat: Format[Map[String, String]] = JsonUtil.StringStringMapFormat

  implicit val logprobsInfoFormat: Format[LogprobsInfo] = Json.format[LogprobsInfo]
  implicit val textCompletionChoiceInfoFormat: Format[TextCompletionChoiceInfo] = Json.format[TextCompletionChoiceInfo]
  implicit val textCompletionFormat: Format[TextCompletionResponse] = Json.format[TextCompletionResponse]

  implicit val textEditChoiceInfoFormat: Format[TextEditChoiceInfo] = Json.format[TextEditChoiceInfo]
  implicit val textEditFormat: Format[TextEditResponse] = Json.format[TextEditResponse]

  implicit val imageFormat: Format[ImageInfo] = Json.format[ImageInfo]

  implicit val embeddingInfoFormat: Format[EmbeddingInfo] = Json.format[EmbeddingInfo]
  implicit val embeddingUsageInfoFormat: Format[EmbeddingUsageInfo] = Json.format[EmbeddingUsageInfo]
  implicit val embeddingFormat: Format[EmbeddingResponse] = Json.format[EmbeddingResponse]

  implicit val fileInfoFormat: Format[FileInfo] = Json.format[FileInfo]

  implicit val fineTuneEventFormat: Format[FineTuneEvent] = Json.format[FineTuneEvent]
  implicit val fineTuneHyperparamsFormat: Format[FineTuneHyperparams] = Json.format[FineTuneHyperparams]
  implicit val fineTuneFormat: Format[FineTuneJob] = Json.format[FineTuneJob]

  implicit val moderationCategoriesFormat: Format[ModerationCategories] = (
    (__ \ "hate").format[Boolean] and
    (__ \ "hate/threatening").format[Boolean] and
    (__ \ "self-harm").format[Boolean] and
    (__ \ "sexual").format[Boolean] and
    (__ \ "sexual/minors").format[Boolean] and
    (__ \ "violence").format[Boolean] and
    (__ \ "violence/graphic").format[Boolean]
  ) (ModerationCategories.apply, unlift(ModerationCategories.unapply))

  implicit val moderationCategoryScoresFormat: Format[ModerationCategoryScores] = (
    (__ \ "hate").format[Double] and
    (__ \ "hate/threatening").format[Double] and
    (__ \ "self-harm").format[Double] and
    (__ \ "sexual").format[Double] and
    (__ \ "sexual/minors").format[Double] and
    (__ \ "violence").format[Double] and
    (__ \ "violence/graphic").format[Double]
  ) (ModerationCategoryScores.apply, unlift(ModerationCategoryScores.unapply))

  implicit val moderationResultFormat: Format[ModerationResult] = Json.format[ModerationResult]
  implicit val moderationFormat: Format[ModerationResponse] = Json.format[ModerationResponse]
}
