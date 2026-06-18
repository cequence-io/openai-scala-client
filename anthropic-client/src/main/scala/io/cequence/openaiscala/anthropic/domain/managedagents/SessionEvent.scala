package io.cequence.openaiscala.anthropic.domain.managedagents

import play.api.libs.json.JsObject

/** Grading rubric for a `user.define_outcome` event. */
sealed trait OutcomeRubric

object OutcomeRubric {
  final case class Text(content: String) extends OutcomeRubric {
    val `type`: String = "text"
  }
  final case class File(fileId: String) extends OutcomeRubric {
    val `type`: String = "file"
  }
}

/**
 * An event a client sends to a session (`POST /v1/sessions/{id}/events`).
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions API</a>
 */
sealed trait SessionEvent

object SessionEvent {

  /** A user message; `text` is wrapped as a single text content block on the wire. */
  final case class UserMessage(text: String) extends SessionEvent {
    val `type`: String = "user.message"
  }

  /** Interrupt the running agent. */
  case object UserInterrupt extends SessionEvent {
    val `type`: String = "user.interrupt"
  }

  /**
   * Approve or deny a tool call awaiting confirmation (`always_ask` policy).
   *
   * @param toolUseId
   *   The triggering event's id.
   * @param allow
   *   `true` → result `"allow"`, `false` → result `"deny"`.
   * @param denyMessage
   *   Optional explanation surfaced to the agent on denial.
   */
  final case class UserToolConfirmation(
    toolUseId: String,
    allow: Boolean,
    denyMessage: Option[String] = None
  ) extends SessionEvent {
    val `type`: String = "user.tool_confirmation"
  }

  /** Result for a custom tool invocation; `text` is wrapped as a text content block. */
  final case class UserCustomToolResult(
    customToolUseId: String,
    text: String,
    isError: Option[Boolean] = None
  ) extends SessionEvent {
    val `type`: String = "user.custom_tool_result"
  }

  /** Start a rubric-graded outcome loop. */
  final case class UserDefineOutcome(
    description: String,
    rubric: OutcomeRubric,
    maxIterations: Option[Int] = None
  ) extends SessionEvent {
    val `type`: String = "user.define_outcome"
  }
}

/**
 * An event received from a session (via list or stream). The received-event union is large and
 * evolving (`agent.*`, `session.status_*`, `span.*`, …); this captures the common envelope
 * fields plus the full raw payload so callers can switch on [[`type`]] and read any field
 * without the client needing an exhaustive typed model.
 */
final case class SessionEventEnvelope(
  `type`: String,
  id: Option[String],
  processedAt: Option[String],
  raw: JsObject
)
