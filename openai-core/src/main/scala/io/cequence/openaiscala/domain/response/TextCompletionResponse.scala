package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class TextCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String], // NEW
  choices: Seq[TextCompletionChoiceInfo],
  usage: Option[UsageInfo]
)

case class TextCompletionChoiceInfo(
  text: String,
  index: Int,
  logprobs: Option[LogprobsInfo],
  finish_reason: Option[String]
)

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
  reasoning_tokens: Int,
  accepted_prediction_tokens: Option[Int],
  rejected_prediction_tokens: Option[Int]
)

case class PromptTokensDetails(
  cached_tokens: Int,
  audio_tokens: Option[Int]
)

case class LogprobsInfo(
  tokens: Seq[String],
  token_logprobs: Seq[Double],
  top_logprobs: Seq[Map[String, Double]],
  text_offset: Seq[Int]
)
