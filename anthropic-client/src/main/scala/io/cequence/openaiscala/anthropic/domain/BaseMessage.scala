package io.cequence.openaiscala.anthropic.domain

final case class BaseMessage(role: ChatRole, content: Seq[Content])
