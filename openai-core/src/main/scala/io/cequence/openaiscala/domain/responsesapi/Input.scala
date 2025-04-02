package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.domain.responsesapi.tools._

trait Input

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
    results: Seq[FileSearchResult] = Nil
  ) = FileSearchToolCall(id, queries, status, results)

  def ofComputerToolCall(
    action: ComputerToolAction,
    callId: String,
    id: String,
    pendingSafetyChecks: Seq[PendingSafetyCheck] = Nil,
    status: ModelStatus
  ) = ComputerToolCall(
    action,
    callId,
    id,
    pendingSafetyChecks,
    status
  )

  def ofComputerToolCallOutput(
    callId: String,
    output: ComputerScreenshot,
    acknowledgedSafetyChecks: Seq[AcknowledgedSafetyCheck] = Nil,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = ComputerToolCallOutput(
    callId,
    output,
    acknowledgedSafetyChecks,
    id,
    status
  )

  def ofWebSearchToolCall(
    id: String,
    status: ModelStatus
  ) = WebSearchToolCall(id, status)

  def ofFunctionToolCall(
    arguments: String,
    callId: String,
    name: String,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = FunctionToolCall(
    arguments,
    callId,
    name,
    id,
    status
  )

  def ofFunctionToolCallOutput(
    callId: String,
    output: String,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ) = FunctionToolCallOutput(
    callId,
    output,
    id,
    status
  )

  def ofReasoning(
    id: String,
    summary: Seq[ReasoningText],
    status: Option[ModelStatus] = None
  ) = Reasoning(id, summary, status)

  def ofItemReference(
    id: String
  ) = ItemReference(id)
}
