package io.cequence.openaiscala.domain.response

sealed trait ToolCall

object ToolCall {
  case object CodeInterpreterCall extends ToolCall
  case object RetrievalCall extends ToolCall

  final case class FunctionCall(
    name: String,
    // TODO: better model?
    arguments: String
  ) extends ToolCall
}
