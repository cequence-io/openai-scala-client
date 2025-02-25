package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.OpenAIScalaClientException
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
  id: String, // gemini openai has this as null
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String],
  choices: Seq[ChatCompletionChoiceInfo],
  usage: Option[UsageInfo],
  originalResponse: Option[Any]
) extends BaseChatCompletionResponse[
      AssistantMessage,
      ChatCompletionChoiceInfo
    ] {

  def contentHead: String = choices.headOption
    .map(_.message.content)
    .getOrElse(
      throw new OpenAIScalaClientException(
        s"No content in the chat completion response ${id}."
      )
    )
}

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
  finish_reason: Option[String],
  logprobs: Option[Logprobs]
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

case class Logprobs(
  content: Seq[LogprobInfo]
)

case class LogprobInfo(
  token: String,
  logprob: Double,
  bytes: Seq[Byte],
  // List of the most likely tokens and their log probability, at this token position.
  // In rare cases, there may be fewer than the number of requested top_logprobs returned.
  top_logprobs: Seq[TopLogprobInfo]
)

case class TopLogprobInfo(
  token: String,
  logprob: Double,
  bytes: Seq[Short]
)

// chunk - streamed
case class ChatCompletionChunkResponse(
  id: String,
  created: ju.Date,
  model: String,
  system_fingerprint: Option[String],
  choices: Seq[ChatCompletionChoiceChunkInfo],
  // TODO: seems to be provided at the end when some flag is set
  usage: Option[UsageInfo]
) {
  def contentHead: Option[String] = choices.headOption
    .map(_.delta.content)
    .getOrElse(
      throw new OpenAIScalaClientException(
        s"No choices in the chat completion response ${id}."
      )
    )
}

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
