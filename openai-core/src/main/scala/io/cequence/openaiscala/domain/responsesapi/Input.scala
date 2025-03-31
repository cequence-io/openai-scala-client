package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.ChatRole

trait Input

sealed trait Inputs

object Inputs {

  case class Text(text: String) extends Inputs

  case class Items(items: Input*) extends Inputs
}

// shortcuts for creating Inputs
object Input {

  def ofInputTextMessage(
    content: String,
    role: ChatRole
  ) = Message.InputText(content, role)

  def ofInputSystemTextMessage(
    content: String
  ) = Message.System(content)

  def ofInputDeveloperTextMessage(
    content: String
  ) = Message.Developer(content)

  def ofInputUserTextMessage(
    content: String
  ) = Message.User(content)

  def ofInputAssistantTextMessage(
    content: String
  ) = Message.Assistant(content)

  def ofInputMessage(
    content: Seq[InputMessageContent],
    role: ChatRole,
    status: Option[ModelStatus] = None
  ) = Message.InputContent(content, role, status)

  def ofOutputMessage(
    content: Seq[OutputMessageContent],
    id: String,
    status: ModelStatus
  ) = Message.OutputContent(content, id, status)

  def ofFileSearchToolCall(
    id: String,
    queries: Seq[String] = Nil,
    status: ModelStatus,
    results: Seq[tools.FileSearchResult] = Nil
  ) = tools.FileSearchToolCall(id, queries, status, results)

  def ofComputerToolCall(
    action: tools.ComputerToolAction,
    callId: String,
    id: String,
    pendingSafetyChecks: Seq[tools.PendingSafetyCheck] = Nil,
    status: ModelStatus
  ) = tools.ComputerToolCall(
    action,
    callId,
    id,
    pendingSafetyChecks,
    status
  )

  def ofComputerToolCallOutput(
    callId: String,
    output: tools.ComputerScreenshot,
    acknowledgedSafetyChecks: Seq[tools.AcknowledgedSafetyCheck] = Nil,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = tools.ComputerToolCallOutput(
    callId,
    output,
    acknowledgedSafetyChecks,
    id,
    status
  )

  def ofWebSearchToolCall(
    id: String,
    status: ModelStatus
  ) = tools.WebSearchToolCall(id, status)

  def ofFunctionToolCall(
    arguments: String,
    callId: String,
    name: String,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = tools.FunctionToolCall(arguments, callId, name, id, status)

  def ofFunctionToolCallOutput(
    callId: String,
    output: String,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = tools.FunctionToolCallOutput(callId, output, id, status)

  def ofReasoning(
    id: String,
    summary: Seq[ReasoningText],
    status: Option[ModelStatus] = None
  ) = Reasoning(id, summary, status)

  def ofItemReference(
    id: String
  ) = ItemReference(id)

}
