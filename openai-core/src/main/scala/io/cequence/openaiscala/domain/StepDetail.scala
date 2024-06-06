package io.cequence.openaiscala.domain

sealed trait StepDetail

object StepDetail {
  case class MessageCreation(messageId: String) extends StepDetail
  case class ToolCalls(messages: BaseMessage) extends StepDetail
}
