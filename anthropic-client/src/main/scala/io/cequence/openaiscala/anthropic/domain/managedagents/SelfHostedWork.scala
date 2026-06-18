package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/** Lifecycle state of a self-hosted work item. */
sealed trait WorkState extends EnumValue

object WorkState {
  case object queued extends WorkState
  case object starting extends WorkState
  case object active extends WorkState
  case object stopping extends WorkState
  case object stopped extends WorkState

  def values: Seq[WorkState] = Seq(queued, starting, active, stopping, stopped)
}

/** The unit of work to perform — currently always a session. */
final case class SessionWorkData(id: String) {
  val `type`: String = "session"
}

/**
 * A unit of work in a self-hosted environment's queue. Queued when sessions are created (or a
 * dormant session gets a new message); the environment worker polls and executes it in your
 * sandbox. These endpoints are normally driven by the SDK/CLI worker, not called directly.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/environments/work">Anthropic Work
 *   API</a>
 */
final case class SelfHostedWork(
  id: String,
  data: SessionWorkData,
  environmentId: String,
  state: WorkState,
  acknowledgedAt: Option[String] = None,
  createdAt: Option[String] = None,
  latestHeartbeatAt: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  startedAt: Option[String] = None,
  stopRequestedAt: Option[String] = None,
  stoppedAt: Option[String] = None
) {
  val `type`: String = "work"
}

/** Response from recording a work heartbeat. */
final case class WorkHeartbeatResponse(
  lastHeartbeat: String,
  leaseExtended: Boolean,
  state: WorkState,
  ttlSeconds: Int
) {
  val `type`: String = "work_heartbeat"
}

/** Work-queue statistics for an environment. */
final case class WorkQueueStats(
  depth: Int,
  pending: Int,
  workersPolling: Int,
  oldestQueuedAt: Option[String] = None
) {
  val `type`: String = "work_queue_stats"
}
