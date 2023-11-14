package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{
  AssistantFunMessage,
  AssistantMessage,
  AssistantToolMessage,
  BaseMessage,
  ChatRole
}

import java.{util => ju}

sealed trait BaseChatCompletionResponse[
  M <: BaseMessage,
  C <: BaseChatCompletionChoiceInfo[M]
] {
  val id: String
  val created: ju.Date
  val model: String
  // new
  val system_fingerprint: Option[String]
  val choices: Seq[C]
  val usage: Option[UsageInfo]
}

case class ChatCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String], // new
  choices: Seq[ChatCompletionChoiceInfo],
  usage: Option[UsageInfo]
) extends BaseChatCompletionResponse[
      AssistantMessage,
      ChatCompletionChoiceInfo
    ]

case class ChatToolCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String], // new
  choices: Seq[ChatToolCompletionChoiceInfo],
  usage: Option[UsageInfo]
) extends BaseChatCompletionResponse[
      AssistantToolMessage,
      ChatToolCompletionChoiceInfo
    ]

case class ChatFunCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String], // new
  choices: Seq[ChatFunCompletionChoiceInfo],
  usage: Option[UsageInfo]
) extends BaseChatCompletionResponse[
      AssistantFunMessage,
      ChatFunCompletionChoiceInfo
    ]

sealed trait BaseChatCompletionChoiceInfo[M <: BaseMessage] {
  val message: M
  val index: Int
  val finish_reason: Option[String]
}

case class ChatCompletionChoiceInfo(
  message: AssistantMessage,
  index: Int,
  finish_reason: Option[String]
) extends BaseChatCompletionChoiceInfo[AssistantMessage]

case class ChatToolCompletionChoiceInfo(
  message: AssistantToolMessage,
  index: Int,
  finish_reason: Option[String]
) extends BaseChatCompletionChoiceInfo[AssistantToolMessage]

case class ChatFunCompletionChoiceInfo(
  message: AssistantFunMessage,
  index: Int,
  finish_reason: Option[String]
) extends BaseChatCompletionChoiceInfo[AssistantFunMessage]

// chunk - streamed
case class ChatCompletionChunkResponse(
  id: String,
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String], // new
  choices: Seq[ChatCompletionChoiceChunkInfo],
  usage: Option[UsageInfo]
)

case class ChatCompletionChoiceChunkInfo(
  delta: ChunkMessageSpec,
  index: Int,
  finish_reason: Option[String]
)

// we should incorporate this into the MessageSpec hierarchy (but the role is optional)
case class ChunkMessageSpec(
  role: Option[ChatRole],
  content: Option[String]
)
