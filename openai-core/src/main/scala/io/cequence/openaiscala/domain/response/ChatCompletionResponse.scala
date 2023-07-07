package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{BaseMessageSpec, ChatRole, FunMessageSpec, MessageSpec}

import java.{util => ju}

sealed trait BaseChatCompletionResponse[
  M <: BaseMessageSpec,
  C <: BaseChatCompletionChoiceInfo[M]
] {
  val id: String
  val created: ju.Date
  val model: String
  val choices: Seq[C]
  val usage: Option[UsageInfo]
}

case class ChatCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  choices: Seq[ChatCompletionChoiceInfo],
  usage: Option[UsageInfo]
) extends BaseChatCompletionResponse[MessageSpec, ChatCompletionChoiceInfo]

case class ChatFunCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  choices: Seq[ChatFunCompletionChoiceInfo],
  usage: Option[UsageInfo]
) extends BaseChatCompletionResponse[FunMessageSpec, ChatFunCompletionChoiceInfo]

sealed trait BaseChatCompletionChoiceInfo[M <: BaseMessageSpec] {
  val message: M
  val index: Int
  val finish_reason: Option[String]
}

case class ChatCompletionChoiceInfo(
  message: MessageSpec,
  index: Int,
  finish_reason: Option[String]
) extends BaseChatCompletionChoiceInfo[MessageSpec]

case class ChatFunCompletionChoiceInfo(
  message: FunMessageSpec,
  index: Int,
  finish_reason: Option[String]
) extends BaseChatCompletionChoiceInfo[FunMessageSpec]

// chunk - streamed
case class ChatCompletionChunkResponse(
  id: String,
  created: ju.Date,
  model: String,
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
