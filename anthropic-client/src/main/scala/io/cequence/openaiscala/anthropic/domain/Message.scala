package io.cequence.openaiscala.anthropic.domain

import io.cequence.openaiscala.anthropic.domain.Content.{
  ContentBlockBase,
  ContentBlocks,
  SingleString
}

sealed abstract class Message private (
  val role: ChatRole,
  val content: Content
) {
  def isSystem: Boolean = role == ChatRole.System
}

object Message {

  case class SystemMessage(
    contentString: String,
    cacheControl: Option[CacheControl] = None
  ) extends Message(ChatRole.System, SingleString(contentString, cacheControl))

  case class SystemMessageContent(contentBlocks: Seq[ContentBlockBase])
      extends Message(ChatRole.System, ContentBlocks(contentBlocks))

  case class UserMessage(
    contentString: String,
    cacheControl: Option[CacheControl] = None
  ) extends Message(ChatRole.User, SingleString(contentString, cacheControl))

  case class UserMessageContent(contentBlocks: Seq[ContentBlockBase])
      extends Message(ChatRole.User, ContentBlocks(contentBlocks))

  case class AssistantMessage(
    contentString: String,
    cacheControl: Option[CacheControl] = None
  ) extends Message(ChatRole.Assistant, SingleString(contentString, cacheControl))

  case class AssistantMessageContent(contentBlocks: Seq[ContentBlockBase])
      extends Message(ChatRole.Assistant, ContentBlocks(contentBlocks))
}
