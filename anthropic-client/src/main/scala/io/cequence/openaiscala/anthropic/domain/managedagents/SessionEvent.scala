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

  /** A user message carrying one or more content blocks (text, image, document). */
  final case class UserMessage(content: Seq[SessionContentBlock]) extends SessionEvent {
    val `type`: String = "user.message"
  }

  object UserMessage {

    /** Convenience constructor for a plain-text user message. */
    def text(text: String): UserMessage =
      UserMessage(Seq(SessionContentBlock.Text(text)))
  }

  /**
   * Interrupt the running agent.
   *
   * @param sessionThreadId
   *   If set, interrupts only that subagent thread; otherwise interrupts all non-archived
   *   threads (multiagent) or the primary thread (single-agent).
   */
  final case class UserInterrupt(
    sessionThreadId: Option[String] = None
  ) extends SessionEvent {
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
   *   Optional explanation surfaced to the agent on denial (only valid when denying).
   * @param sessionThreadId
   *   Routes the confirmation to a subagent thread; echo from the tool-use event.
   */
  final case class UserToolConfirmation(
    toolUseId: String,
    allow: Boolean,
    denyMessage: Option[String] = None,
    sessionThreadId: Option[String] = None
  ) extends SessionEvent {
    val `type`: String = "user.tool_confirmation"
  }

  /**
   * Result for a sandbox-routed (client-executed) tool call. Valid only on `self_hosted`
   * environments.
   *
   * @param toolUseId
   *   The id from the `agent.tool_use` event.
   */
  final case class UserToolResult(
    toolUseId: String,
    content: Seq[SessionContentBlock] = Nil,
    isError: Option[Boolean] = None,
    sessionThreadId: Option[String] = None
  ) extends SessionEvent {
    val `type`: String = "user.tool_result"
  }

  /** Result for a custom tool invocation, as one or more content blocks. */
  final case class UserCustomToolResult(
    customToolUseId: String,
    content: Seq[SessionContentBlock] = Nil,
    isError: Option[Boolean] = None,
    sessionThreadId: Option[String] = None
  ) extends SessionEvent {
    val `type`: String = "user.custom_tool_result"
  }

  object UserCustomToolResult {

    /** Convenience constructor for a plain-text custom-tool result. */
    def text(
      customToolUseId: String,
      text: String,
      isError: Option[Boolean] = None
    ): UserCustomToolResult =
      UserCustomToolResult(customToolUseId, Seq(SessionContentBlock.Text(text)), isError)
  }

  /** Start a rubric-graded outcome loop. */
  final case class UserDefineOutcome(
    description: String,
    rubric: OutcomeRubric,
    maxIterations: Option[Int] = None
  ) extends SessionEvent {
    val `type`: String = "user.define_outcome"
  }

  /**
   * A mid-conversation system message (text-only). Must be the final event in a request and
   * immediately follow a `user.message`/`user.tool_result`/`user.custom_tool_result`.
   */
  final case class SystemMessage(
    content: Seq[SessionContentBlock.Text]
  ) extends SessionEvent {
    val `type`: String = "system.message"
  }

  object SystemMessage {

    /** Convenience constructor for a plain-text system message. */
    def text(text: String): SystemMessage =
      SystemMessage(Seq(SessionContentBlock.Text(text)))
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
