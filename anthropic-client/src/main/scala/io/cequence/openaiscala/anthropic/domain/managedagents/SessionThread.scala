package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/** Status of a subagent thread within a multiagent session. */
sealed trait SessionThreadStatus extends EnumValue

object SessionThreadStatus {
  case object rescheduling extends SessionThreadStatus
  case object running extends SessionThreadStatus
  case object idle extends SessionThreadStatus
  case object terminated extends SessionThreadStatus

  def values: Seq[SessionThreadStatus] =
    Seq(rescheduling, running, idle, terminated)
}

/**
 * A per-subagent thread within a multiagent session — a context-isolated event stream with its
 * own conversation history.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions API</a>
 */
final case class SessionThread(
  id: String,
  status: SessionThreadStatus,
  sessionId: Option[String] = None,
  agentId: Option[String] = None,
  parentThreadId: Option[String] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "thread"
}
