package io.cequence.openaiscala.domain.agents

import play.api.libs.json.{JsObject, JsValue}

import java.{util => ju}

/**
 * A new session (`POST /agents/sessions`): an inline [[agent]] configuration or a reusable
 * agent's [[agentId]], and its [[environment]]. The first input, if any, is passed with the
 * create call; the session is deleted with `deleteAgentSession`.
 *
 * @param vaultIds
 *   vaults whose credentials the session's tools may use
 */
final case class CreateAgentSessionSettings(
  agent: Option[AgentConfig] = None,
  agentId: Option[String] = None,
  environment: AgentEnvironment = AgentEnvironment.NoEnvironment,
  vaultIds: Seq[String] = Nil,
  metadata: Map[String, String] = Map()
)

/** Input content for an agent: text or an image. */
sealed trait AgentInputContent

object AgentInputContent {
  final case class Text(text: String) extends AgentInputContent

  /** An image as a data URL or http(s) URL. */
  final case class Image(imageUrl: String) extends AgentInputContent
}

/** A user message to an agent. */
final case class AgentUserMessage(content: Seq[AgentInputContent])

object AgentUserMessage {
  def text(text: String): AgentUserMessage = AgentUserMessage(
    Seq(AgentInputContent.Text(text))
  )
}

/** The first input of a session: plain text or user messages. */
sealed trait AgentInput

object AgentInput {
  final case class Text(text: String) extends AgentInput
  final case class Messages(messages: Seq[AgentUserMessage]) extends AgentInput
}

/** The status of a session - see [[AgentSession.status]]. */
object AgentSessionStatus {

  /** No turn in progress, ready for input (a hosted environment may still provision). */
  val Idle = "idle"
  val InProgress = "in_progress"

  /** Waiting for [[AgentSession.requiredActions]], e.g. a client function's result. */
  val RequiresAction = "requires_action"
  val Failed = "failed"
}

/**
 * A session: a durable instance of an agent working on tasks.
 *
 * @param status
 *   see [[AgentSessionStatus]] (a string - the list may grow)
 * @param environment
 *   the session's environment as returned (`type`, `id`, ...)
 * @param usage
 *   best-effort token usage (often absent)
 */
final case class AgentSession(
  id: String,
  status: String,
  createdAt: ju.Date,
  lastActiveAt: Option[ju.Date] = None,
  requiredActions: Seq[AgentRequiredAction] = Nil,
  error: Option[String] = None,
  agent: Option[Agent] = None,
  environment: Option[JsObject] = None,
  vaultIds: Seq[String] = Nil,
  usage: Option[AgentTokenUsage] = None,
  metadata: Map[String, String] = Map()
) {
  def isIdle: Boolean = status == AgentSessionStatus.Idle
  def requiresAction: Boolean = status == AgentSessionStatus.RequiresAction
  def isFailed: Boolean = status == AgentSessionStatus.Failed

  /** The client function calls the session waits for. */
  def pendingFunctionCalls: Seq[AgentRequiredAction.FunctionCall] =
    requiredActions.collect { case call: AgentRequiredAction.FunctionCall => call }
}

/** What a session in `requires_action` waits for. */
sealed trait AgentRequiredAction

object AgentRequiredAction {

  /**
   * A client function call to answer with an [[AgentSessionInput.ToolResult]].
   *
   * @param arguments
   *   the arguments as a JSON object (not a string)
   */
  final case class FunctionCall(
    turnId: String,
    callId: String,
    name: String,
    arguments: JsValue
  ) extends AgentRequiredAction

  /** A browser authentication / origin-access request of computer use. */
  final case class ComputerUseApprovalRequest(
    turnId: String,
    requestId: String,
    request: JsValue
  ) extends AgentRequiredAction

  /** A self-hosted environment to connect. */
  final case class EnvironmentConnection(
    environmentId: String
  ) extends AgentRequiredAction

  /** A required action this client does not model. */
  final case class Other(
    actionType: String,
    raw: JsObject
  ) extends AgentRequiredAction
}

/**
 * A turn of a session (or of one of its subagents).
 *
 * @param status
 *   `queued`, `in_progress`, `waiting`, `completed`, `failed` or `cancelled`
 */
final case class AgentTurn(
  id: String,
  sessionId: String,
  status: String,
  agentId: Option[String] = None,
  subagentId: Option[String] = None,
  createdAt: Option[ju.Date] = None,
  startedAt: Option[ju.Date] = None,
  completedAt: Option[ju.Date] = None,
  error: Option[AgentTurnError] = None,
  usage: Option[AgentTokenUsage] = None
)

/**
 * Why a turn failed - `code` is e.g. `context_length_exceeded`, `rate_limit_exceeded`,
 * `server_overloaded`, `sandbox_error`, `request_timeout`, `internal_error`.
 */
final case class AgentTurnError(
  code: String,
  message: String
)

/** Best-effort token usage of a session or turn. */
final case class AgentTokenUsage(
  inputTokens: Int,
  outputTokens: Int,
  totalTokens: Int,
  inputTokensDetails: Option[JsObject] = None,
  outputTokensDetails: Option[JsObject] = None
)

/**
 * A subagent of a multi-agent session.
 *
 * @param status
 *   e.g. `open` / `closed`
 */
final case class AgentSubagent(
  id: String,
  sessionId: String,
  status: String,
  parentAgentId: Option[String] = None,
  name: Option[String] = None,
  instructions: Option[JsValue] = None,
  openedAt: Option[ju.Date] = None,
  closedAt: Option[ju.Date] = None
)

/** A file an agent produced in its environment. */
final case class AgentSessionArtifact(
  id: String,
  sessionId: String,
  path: String,
  sizeBytes: Long,
  environmentId: Option[String] = None,
  turnId: Option[String] = None,
  createdAt: Option[ju.Date] = None
)

/**
 * A reusable environment template (`packages`, `network`, `desktop`, `skills`, `plugins`,
 * `files` ... in [[raw]]).
 */
final case class AgentEnvironmentTemplate(
  id: String,
  name: Option[String],
  raw: JsObject
)

/** A page of a list endpoint. */
final case class AgentsPage[T](
  data: Seq[T],
  firstId: Option[String] = None,
  lastId: Option[String] = None,
  hasMore: Boolean = false
)

/** The answer of a delete call. */
final case class AgentsDeleted(
  id: String,
  deleted: Boolean
)
