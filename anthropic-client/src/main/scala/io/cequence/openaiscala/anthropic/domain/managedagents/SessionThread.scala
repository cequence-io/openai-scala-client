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

/** Wall-clock timing breakdown for a session thread. */
final case class SessionThreadStats(
  activeSeconds: Option[Double] = None,
  durationSeconds: Option[Double] = None,
  startupSeconds: Option[Double] = None
)

/** Ephemeral cache-creation token breakdown. */
final case class ManagedAgentsCacheCreation(
  ephemeral1hInputTokens: Option[Int] = None,
  ephemeral5mInputTokens: Option[Int] = None
)

/** Token usage accumulated by a session thread. */
final case class SessionThreadUsage(
  cacheCreation: Option[ManagedAgentsCacheCreation] = None,
  cacheReadInputTokens: Option[Int] = None,
  inputTokens: Option[Int] = None,
  outputTokens: Option[Int] = None
)

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
  agent: Option[Agent] = None,
  parentThreadId: Option[String] = None,
  stats: Option[SessionThreadStats] = None,
  usage: Option[SessionThreadUsage] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "session_thread"
}
