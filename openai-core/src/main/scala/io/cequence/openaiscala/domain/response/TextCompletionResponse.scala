package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class TextCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  choices: Seq[TextCompletionChoiceInfo],
  usage: UsageInfo
)

case class TextCompletionChoiceInfo(
  text: String,
  index: Int,
  logprobs: Option[LogprobsInfo],
  finish_reason: String
)

case class UsageInfo(
  prompt_tokens: Int,
  total_tokens: Int,
  completion_tokens: Option[Int]
)

case class LogprobsInfo(
  tokens: Seq[String],
  token_logprobs: Seq[Double],
  top_logprobs: Seq[Map[String, Double]],
  text_offset: Seq[Int]
)