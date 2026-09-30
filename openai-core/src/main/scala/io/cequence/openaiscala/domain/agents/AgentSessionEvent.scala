package io.cequence.openaiscala.domain.agents

import play.api.libs.json.{JsObject, JsValue}

/**
 * A server-sent event of a session - the stream of a created session
 * (`createAgentSessionStreamed`, which the server closes once the session idles) or the
 * subscription to its events (`streamAgentSessionEvents`, held open). Dispatched on the JSON
 * `type`; every event this client does not model (`content_part.*`, `environment.*`,
 * `subagent.*`, ...) arrives as [[AgentSessionEvent.Other]] with the raw JSON.
 */
sealed trait AgentSessionEvent {
  def eventType: String
  def eventId: Option[String]
}

object AgentSessionEvent {

  /**
   * The session changed - `agent.session.created` / `.in_progress` / `.idle` /
   * `.requires_action` / `.failed`, with the whole [[AgentSession]] (its `requiredActions` on
   * `requires_action`).
   */
  final case class SessionUpdated(
    eventType: String,
    eventId: Option[String],
    session: AgentSession
  ) extends AgentSessionEvent {
    def isIdle: Boolean = eventType == SessionIdle
    def requiresAction: Boolean = eventType == SessionRequiresAction
    def isFailed: Boolean = eventType == SessionFailed
  }

  /**
   * A turn changed - `agent.session.turn.created` / `.in_progress` / `.completed` / `.failed`
   * / `.cancelled`.
   */
  final case class TurnUpdated(
    eventType: String,
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    turn: AgentTurn,
    usage: Option[AgentTokenUsage] = None
  ) extends AgentSessionEvent {
    def isFinished: Boolean =
      Seq(TurnCompleted, TurnFailed, TurnCancelled).contains(eventType)
  }

  /** `agent.session.turn.item.added` - `item` is the item as it starts. */
  final case class ItemAdded(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    outputIndex: Int,
    item: AgentSessionItem
  ) extends AgentSessionEvent {
    val eventType = "agent.session.turn.item.added"
  }

  /** `agent.session.turn.item.done` - the complete item. */
  final case class ItemDone(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    outputIndex: Int,
    item: AgentSessionItem
  ) extends AgentSessionEvent {
    val eventType = "agent.session.turn.item.done"
  }

  final case class OutputTextDelta(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    delta: String
  ) extends AgentSessionEvent {
    val eventType = "agent.session.turn.output_text.delta"
  }

  /**
   * The complete text of a message part. A subscription opened mid-turn replays the turn's
   * finished parts with this event only - no deltas (live-verified 2026-09-30).
   */
  final case class OutputTextDone(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    text: String
  ) extends AgentSessionEvent {
    val eventType = "agent.session.turn.output_text.done"
  }

  final case class ReasoningSummaryTextDelta(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    itemId: String,
    outputIndex: Int,
    summaryIndex: Int,
    delta: String
  ) extends AgentSessionEvent {
    val eventType = "agent.session.turn.reasoning_summary_text.delta"
  }

  final case class ReasoningSummaryTextDone(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    itemId: String,
    outputIndex: Int,
    summaryIndex: Int,
    text: String
  ) extends AgentSessionEvent {
    val eventType = "agent.session.turn.reasoning_summary_text.done"
  }

  /** The output of a command running in the environment, as it streams. */
  final case class CommandExecutionOutputDelta(
    eventId: Option[String],
    sessionId: String,
    turnId: String,
    itemId: String,
    outputIndex: Int,
    delta: String
  ) extends AgentSessionEvent {
    val eventType = "agent.output.command_execution_output.delta"
  }

  /** An `error` event of the session. */
  final case class Error(
    eventId: Option[String],
    sessionId: Option[String],
    error: JsValue
  ) extends AgentSessionEvent {
    val eventType = "error"

    def message: String =
      (error \ "message").asOpt[String].getOrElse(error.toString)
  }

  /** Any other event, as its JSON. */
  final case class Other(
    eventType: String,
    raw: JsObject
  ) extends AgentSessionEvent {
    def eventId: Option[String] = (raw \ "event_id").asOpt[String]
  }

  val SessionCreated = "agent.session.created"
  val SessionInProgress = "agent.session.in_progress"
  val SessionIdle = "agent.session.idle"
  val SessionRequiresAction = "agent.session.requires_action"
  val SessionFailed = "agent.session.failed"

  val TurnCreated = "agent.session.turn.created"
  val TurnInProgress = "agent.session.turn.in_progress"
  val TurnCompleted = "agent.session.turn.completed"
  val TurnFailed = "agent.session.turn.failed"
  val TurnCancelled = "agent.session.turn.cancelled"
}
