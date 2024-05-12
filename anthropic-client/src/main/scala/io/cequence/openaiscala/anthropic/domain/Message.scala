package io.cequence.openaiscala.anthropic.domain

import io.cequence.openaiscala.anthropic.domain.Content.{
  ContentBlock,
  ContentBlocks,
  SingleString
}

sealed abstract class Message private (
  val role: ChatRole,
  val content: Content
)

object Message {

  case class UserMessage(contentString: String)
      extends Message(ChatRole.User, SingleString(contentString))
  case class UserMessageContent(contentBlocks: Seq[ContentBlock])
      extends Message(ChatRole.User, ContentBlocks(contentBlocks))
  case class AssistantMessage(contentString: String)
      extends Message(ChatRole.Assistant, SingleString(contentString))
  case class AssistantMessageContent(contentBlocks: Seq[ContentBlock])
      extends Message(ChatRole.Assistant, ContentBlocks(contentBlocks))
}
