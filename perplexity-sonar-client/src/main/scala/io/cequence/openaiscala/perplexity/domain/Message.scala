package io.cequence.openaiscala.perplexity.domain

sealed trait Message {
  def role: ChatRole
  def content: String
}

object Message {

  final case class SystemMessage(content: String) extends Message {
    val role: ChatRole = ChatRole.System
  }

  final case class UserMessage(content: String) extends Message {
    val role: ChatRole = ChatRole.User
  }

  final case class AssistantMessage(content: String) extends Message {
    val role: ChatRole = ChatRole.Assistant
  }
}
