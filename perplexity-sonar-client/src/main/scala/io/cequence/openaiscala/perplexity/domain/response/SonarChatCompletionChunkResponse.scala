package io.cequence.openaiscala.perplexity.domain.response

import io.cequence.openaiscala.domain.response.{ChatCompletionChoiceChunkInfo, UsageInfo}

import java.{util => ju}

case class SonarChatCompletionChunkResponse(
  id: String,
  created: ju.Date,
  model: String,
  citations: Seq[String],
  choices: Seq[ChatCompletionChoiceChunkInfo],
  usage: Option[UsageInfo]
)
