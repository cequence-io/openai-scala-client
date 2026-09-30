package io.cequence.openaiscala.domain.agents

import play.api.libs.json.{JsObject, JsValue}

/**
 * An item of a session's turn: a message, reasoning, a (client or server) tool call and its
 * result, a command run in the environment, ... The items this client does not model - an
 * `agent_message` between agents, the subagent calls (`create_subagent_call`,
 * `wait_for_subagents_call`, ...), computer use - are [[AgentSessionItem.Other]] with the raw
 * JSON, never a parse failure.
 */
sealed trait AgentSessionItem {
  def itemType: String
  def id: Option[String]
  def turnId: Option[String]
}

object AgentSessionItem {

  /**
   * A user or assistant message.
   *
   * @param phase
   *   an assistant message's phase - `commentary` (progress while working) or `final_answer`
   * @param status
   *   `in_progress` / `completed` / `incomplete`
   */
  final case class Message(
    id: Option[String],
    turnId: Option[String],
    role: String,
    content: Seq[AgentMessageContent] = Nil,
    status: Option[String] = None,
    phase: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "message"

    def text: String =
      content.collect {
        case AgentMessageContent.OutputText(text) => text
        case AgentMessageContent.InputText(text)  => text
      }.mkString

    def isFinalAnswer: Boolean = phase.contains(AgentMessagePhase.FinalAnswer)
  }

  final case class Reasoning(
    id: Option[String],
    turnId: Option[String],
    summary: Option[JsValue] = None,
    status: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "reasoning"
  }

  /**
   * A client function call (the session waits for its result - see
   * [[AgentRequiredAction.FunctionCall]]).
   *
   * @param arguments
   *   the arguments as JSON (an object)
   */
  final case class FunctionCall(
    id: Option[String],
    turnId: Option[String],
    callId: String,
    name: String,
    arguments: JsValue,
    status: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "function_call"
  }

  final case class FunctionCallOutput(
    id: Option[String],
    turnId: Option[String],
    callId: String,
    output: Option[JsValue] = None,
    error: Option[String] = None,
    status: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "function_call_output"
  }

  final case class McpCall(
    id: Option[String],
    turnId: Option[String],
    serverLabel: String,
    name: String,
    arguments: Option[JsValue] = None,
    output: Option[JsValue] = None,
    error: Option[JsValue] = None,
    status: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "mcp_call"
  }

  /** A command the agent ran in its environment. */
  final case class CommandExecution(
    id: Option[String],
    turnId: Option[String],
    command: String,
    cwd: Option[String] = None,
    output: Option[String] = None,
    exitCode: Option[Int] = None,
    durationMs: Option[Long] = None,
    status: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "command_execution"
  }

  final case class WebSearchCall(
    id: Option[String],
    turnId: Option[String],
    action: Option[JsValue] = None,
    status: Option[String] = None
  ) extends AgentSessionItem {
    val itemType = "web_search_call"
  }

  /** Any other item, as its JSON. */
  final case class Other(
    itemType: String,
    raw: JsObject
  ) extends AgentSessionItem {
    def id: Option[String] = (raw \ "id").asOpt[String]
    def turnId: Option[String] = (raw \ "turn_id").asOpt[String]
  }
}

object AgentMessagePhase {
  val Commentary = "commentary"
  val FinalAnswer = "final_answer"
}

sealed trait AgentMessageContent

object AgentMessageContent {
  final case class InputText(text: String) extends AgentMessageContent
  final case class InputImage(imageUrl: String) extends AgentMessageContent
  final case class OutputText(text: String) extends AgentMessageContent
  final case class Other(raw: JsObject) extends AgentMessageContent
}
