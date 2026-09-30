package io.cequence.openaiscala.domain.agents

import play.api.libs.json.JsObject

/**
 * An input event sent to a session (`POST /agents/sessions/{id}/events`).
 */
sealed trait AgentSessionInput

object AgentSessionInput {

  /** New user messages - starts a turn (or steers the active one). */
  final case class Message(messages: Seq[AgentUserMessage]) extends AgentSessionInput

  /** Cancels the active turn (needed before a session paused mid-turn can be deleted). */
  case object Cancel extends AgentSessionInput

  /**
   * The result of a client function call the session waits for (see
   * [[AgentRequiredAction.FunctionCall]]) - `output` as text, or `outputContent`.
   */
  final case class ToolResult(
    turnId: String,
    callId: String,
    success: Boolean = true,
    output: Option[String] = None,
    outputContent: Seq[AgentInputContent] = Nil,
    error: Option[String] = None
  ) extends AgentSessionInput

  /**
   * The answer to a computer-use approval request (`browser_authentication` /
   * `browser_origin_access`) as JSON.
   */
  final case class ComputerUseApprovalResult(
    requestId: String,
    response: JsObject
  ) extends AgentSessionInput

  def text(text: String): Message = Message(Seq(AgentUserMessage.text(text)))

  /** A successful text result of a pending function call. */
  def toolResult(
    call: AgentRequiredAction.FunctionCall,
    output: String
  ): ToolResult =
    ToolResult(call.turnId, call.callId, success = true, output = Some(output))
}
