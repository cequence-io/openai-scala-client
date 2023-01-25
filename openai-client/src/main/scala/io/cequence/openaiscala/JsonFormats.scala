package io.cequence.openaiscala

import io.cequence.openaiscala.domain.response._
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, _}

object JsonFormats {
  private implicit val dateFormat = JsonUtil.SecDateFormat

  implicit val PermissionFormat = Json.format[Permission]
  implicit val modelSpecFormat = Json.format[ModelInfo]

  implicit val usageInfoFormat = Json.format[UsageInfo]

  private implicit val stringDoubleMapFormat = JsonUtil.StringDoubleMapFormat
  implicit val logprobsInfoFormat = Json.format[LogprobsInfo]
  implicit val textCompletionChoiceInfoFormat = Json.format[TextCompletionChoiceInfo]
  implicit val textCompletionFormat = Json.format[TextCompletionResponse]

  implicit val textEditChoiceInfoFormat = Json.format[TextEditChoiceInfo]
  implicit val textEditFormat = Json.format[TextEditResponse]

  implicit val imageFormat = Json.format[ImageInfo]

  implicit val embeddingInfoFormat = Json.format[EmbeddingInfo]
  implicit val embeddingUsageInfoFormat = Json.format[EmbeddingUsageInfo]
  implicit val embeddingFormat = Json.format[EmbeddingResponse]

  implicit val fileInfoFormat = Json.format[FileInfo]

  implicit val fineTuneEventFormat = Json.format[FineTuneEvent]
  implicit val fineTuneHyperparamsFormat = Json.format[FineTuneHyperparams]
  implicit val fineTuneFormat = Json.format[FineTuneJob]

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

  implicit val moderationResultFormat = Json.format[ModerationResult]
  implicit val moderationFormat = Json.format[ModerationResponse]
}
