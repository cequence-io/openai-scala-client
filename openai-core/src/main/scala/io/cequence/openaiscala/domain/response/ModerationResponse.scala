package io.cequence.openaiscala.domain.response

case class ModerationResponse(
  id: String,
  model: String,
  results: Seq[ModerationResult]
)

case class ModerationResult(
  categories: ModerationCategories,
  category_scores: ModerationCategoryScores,
  flagged: Boolean
)

case class ModerationCategories(
  hate: Boolean,
  hate_threatening: Boolean,
  self_harm: Boolean,
  sexual: Boolean,
  sexual_minors: Boolean,
  violence: Boolean,
  violence_graphic: Boolean
)

case class ModerationCategoryScores(
  hate: Double,
  hate_threatening: Double,
  self_harm: Double,
  sexual: Double,
  sexual_minors: Double,
  violence: Double,
  violence_graphic: Double
)
