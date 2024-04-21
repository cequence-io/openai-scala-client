package io.cequence.openaiscala.v2.domain.response

import java.{util => ju}

case class TextEditResponse(
  created: ju.Date,
  choices: Seq[TextEditChoiceInfo],
  usage: UsageInfo
)

case class TextEditChoiceInfo(
  text: String,
  index: Int,
  logprobs: Option[LogprobsInfo]
)
