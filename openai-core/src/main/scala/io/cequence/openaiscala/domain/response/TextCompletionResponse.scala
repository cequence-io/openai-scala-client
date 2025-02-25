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

case class LogprobsInfo(
  tokens: Seq[String],
  token_logprobs: Seq[Double],
  top_logprobs: Seq[Map[String, Double]],
  text_offset: Seq[Int]
)
