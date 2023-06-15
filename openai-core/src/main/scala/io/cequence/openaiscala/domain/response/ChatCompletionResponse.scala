package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.ChatRole

import java.{util => ju}

case class ChatCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  choices: Seq[ChatCompletionChoiceInfo],
  usage: Option[UsageInfo]
)

case class ChatCompletionChoiceInfo(
  message: ChatMessage,
  index: Int,
  finish_reason: Option[String]
)

// chunk - streamed
case class ChatCompletionChunkResponse(
  id: String,
  created: ju.Date,
  model: String,
  choices: Seq[ChatCompletionChoiceChunkInfo],
  usage: Option[UsageInfo]
)

case class ChatCompletionChoiceChunkInfo(
  delta: ChatChunkMessage,
  index: Int,
  finish_reason: Option[String]
)

case class ChatChunkMessage(
  role: Option[ChatRole],
  content: Option[String]
)