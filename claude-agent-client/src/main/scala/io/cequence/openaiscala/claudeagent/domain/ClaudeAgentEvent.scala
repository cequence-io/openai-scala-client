package io.cequence.openaiscala.claudeagent.domain

import play.api.libs.json.JsObject

/**
 * An event received from a `claude` CLI subprocess session. The received-message union is
 * large (30+ variants: hook/task/plugin/mcp bookkeeping, session-state changes, thinking-token
 * accounting, ...) and evolves across CLI versions - mirrors the design of
 * [[io.cequence.openaiscala.anthropic.domain.managedagents.SessionEventEnvelope]]: a handful
 * of "core" conversational message types are modeled precisely, everything else falls through
 * to [[ClaudeAgentEvent.Unknown]] carrying the raw payload so callers can still switch on
 * `type`/`subtype` and read any field without an exhaustive typed model.
 */
sealed trait ClaudeAgentEvent {
  def raw: JsObject
}

object ClaudeAgentEvent {

  /** First message the CLI emits - readiness/handshake signal. */
  case class SystemInit(
    apiKeySource: String,
    model: String,
    tools: Seq[String],
    permissionMode: String,
    sessionId: String,
    cwd: String,
    claudeCodeVersion: String,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /**
   * An assistant turn. `message` is the raw Anthropic Messages API `BetaMessage` JSON object
   * (id, role, content, stop_reason, usage, ...) - parse `(message \ "content")` with
   * [[io.cequence.openaiscala.anthropic.JsonFormats.contentBlockFormat]] (a
   * `Seq[io.cequence.openaiscala.anthropic.domain.Content.ContentBlock]`) if typed content
   * blocks are needed; kept raw here to avoid coupling every event's construction to full
   * content-block parsing succeeding.
   */
  case class Assistant(
    message: JsObject,
    parentToolUseId: Option[String],
    sessionId: String,
    uuid: String,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /** Echo of a user turn (including replayed history on --resume). */
  case class UserEcho(
    sessionId: Option[String],
    isReplay: Boolean,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /** End of one turn, successful. NOT the end of the session - the process stays alive. */
  case class ResultSuccess(
    result: String,
    totalCostUsd: Double,
    numTurns: Int,
    durationMs: Long,
    isError: Boolean,
    sessionId: String,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /**
   * End of one turn, failed (subtype one of error_during_execution / error_max_turns /
   * error_max_budget_usd / error_max_structured_output_retries).
   */
  case class ResultError(
    subtype: String,
    errors: Seq[String],
    totalCostUsd: Double,
    sessionId: String,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /**
   * Token-level partial-assistant-message delta (only emitted when
   * settings.includePartialMessages = true). Wraps the raw Anthropic streaming event
   * (message_start/delta/stop, content_block_start/delta/stop).
   */
  case class StreamDelta(
    sessionId: String,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /**
   * The CLI is asking the host to approve or deny a tool call (received as a
   * `control_request{subtype:"can_use_tool"}` from the CLI, NOT a plain conversational
   * message). Reply via `ClaudeAgentService.respondToolPermission(requestId, decision)`.
   */
  case class ToolPermissionRequest(
    requestId: String,
    toolName: String,
    input: JsObject,
    toolUseId: String,
    raw: JsObject
  ) extends ClaudeAgentEvent

  /** Fallback for every other message/control-request type not modeled above. */
  case class Unknown(
    `type`: String,
    subtype: Option[String],
    raw: JsObject
  ) extends ClaudeAgentEvent
}
