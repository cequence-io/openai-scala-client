package io.cequence.openaiscala.perplexity.domain.settings

import io.cequence.wsclient.domain.EnumValue

/**
 * Settings for creating a chat completion via Sonar.
 *
 * @param model
 *   The name of the model that will complete your prompt (required).
 * @param frequency_penalty
 *   A multiplicative penalty (> 0). Values > 1.0 penalize new tokens based on frequency,
 *   decreasing the likelihood of repeating the same line verbatim. Default = 1.0. Incompatible
 *   with presence_penalty.
 * @param max_tokens
 *   The maximum number of completion tokens returned by the API. If left unspecified, the
 *   model will generate tokens until it reaches a stop token or the end of its context window.
 * @param presence_penalty
 *   A value (-2 < x < 2) that penalizes new tokens based on whether they appear in the text so
 *   far, increasing the likelihood to talk about new topics. Default = 0.0. Incompatible with
 *   frequency_penalty.
 * @param response_format
 *   Enable structured outputs with a JSON or Regex schema. If provided, it should conform to
 *   the required format.
 * @param return_images
 *   Determines whether or not a request to an online model should return images. Default =
 *   false.
 * @param return_related_questions
 *   Determines whether or not a request to an online model should return related questions.
 *   Default = false.
 * @param search_domain_filter
 *   A list of domains to limit citations to (whitelist) or exclude (blacklist via a leading
 *   "-"). Currently limited to only 3 domains total.
 * @param search_recency_filter
 *   Returns search results within the specified time interval (e.g., "month", "week", "day",
 *   "hour"). Does not apply to images.
 * @param temperature
 *   The amount of randomness in the response, (0 < x < 2). Higher values are more random;
 *   lower values are more deterministic. Default = 0.2.
 * @param top_k
 *   The number of tokens to keep for highest top-k filtering (0 <= x <= 2048). If 0, top-k
 *   filtering is disabled. Default = 0.
 * @param top_p
 *   The nucleus sampling threshold, (0 < x <= 1). For each token, the model considers the
 *   tokens with top_p probability mass. We recommend altering either top_k or top_p, but not
 *   both. Default = 0.9.
 */
case class SonarCreateChatCompletionSettings(
  model: String,
  frequency_penalty: Option[Double] = None,
  max_tokens: Option[Int] = None,
  presence_penalty: Option[Double] = None,
  response_format: Option[SolarResponseFormatType] = None,
  return_images: Option[Boolean] = None,
  return_related_questions: Option[Boolean] = None,
  search_domain_filter: Seq[String] = Nil,
  search_recency_filter: Option[RecencyFilterType] = None,
  temperature: Option[Double] = None,
  top_k: Option[Int] = None,
  top_p: Option[Double] = None
)

trait SolarResponseFormatType extends EnumValue

object SolarResponseFormatType {
  case object json_schema extends SolarResponseFormatType
  case object regex extends SolarResponseFormatType

  def values: Seq[SolarResponseFormatType] = Seq(json_schema, regex)
}

trait RecencyFilterType extends EnumValue

object RecencyFilterType {
  case object month extends RecencyFilterType
  case object week extends RecencyFilterType
  case object day extends RecencyFilterType
  case object hour extends RecencyFilterType

  def values: Seq[RecencyFilterType] = Seq(month, week, day, hour)
}
