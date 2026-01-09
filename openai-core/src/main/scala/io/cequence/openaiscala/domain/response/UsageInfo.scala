package io.cequence.openaiscala.domain.response

case class UsageInfo(
  prompt_tokens: Int,
  total_tokens: Int,
  completion_tokens: Option[Int],
  prompt_tokens_details: Option[PromptTokensDetails] = None,
  completion_tokens_details: Option[CompletionTokenDetails] = None
  //  prompt_cache_hit_tokens: Option[Int],
  //  prompt_cache_miss_tokens: Option[Int]
)

case class CompletionTokenDetails(
  reasoning_tokens: Option[Int] = None,
  accepted_prediction_tokens: Option[Int] = None,
  rejected_prediction_tokens: Option[Int] = None
)

case class PromptTokensDetails(
  cached_tokens: Int,
  audio_tokens: Option[Int]
)
