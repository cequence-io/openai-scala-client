package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/**
 * A reference to an agent (optionally version-pinned). On the wire: `{type:"agent", id,
 * version?}`.
 */
final case class AgentReference(
  id: String,
  version: Option[Int] = None
) {
  val `type`: String = "agent"
}

/** Deployment status. */
sealed trait DeploymentStatus extends EnumValue

object DeploymentStatus {
  case object active extends DeploymentStatus
  case object paused extends DeploymentStatus

  def values: Seq[DeploymentStatus] = Seq(active, paused)
}

/** Why a deployment is paused. */
sealed trait DeploymentPausedReason

object DeploymentPausedReason {

  /** Paused by a user. */
  case object Manual extends DeploymentPausedReason {
    val `type`: String = "manual"
  }

  /**
   * Auto-paused due to an error (e.g. `environment_archived_error`, `agent_archived_error`,
   * `vault_not_found_error`, …).
   */
  final case class Error(errorType: String) extends DeploymentPausedReason {
    val `type`: String = "error"
  }
}

/** A cron schedule for triggering deployment runs. */
final case class Schedule(
  expression: String,
  timezone: String,
  lastRunAt: Option[String] = None,
  upcomingRunsAt: Seq[String] = Nil
) {
  val `type`: String = "cron"
}

/** An event seeded into each deployment run. */
sealed trait DeploymentInitialEvent

object DeploymentInitialEvent {

  /** A user message; `text` is wrapped as a single text content block. */
  final case class UserMessage(text: String) extends DeploymentInitialEvent {
    val `type`: String = "user.message"
  }

  /** A system message; `text` is wrapped as a single text content block. */
  final case class SystemMessage(text: String) extends DeploymentInitialEvent {
    val `type`: String = "system.message"
  }

  /** A rubric-graded outcome to pursue each run. */
  final case class UserDefineOutcome(
    description: String,
    rubric: OutcomeRubric,
    maxIterations: Option[Int] = None
  ) extends DeploymentInitialEvent {
    val `type`: String = "user.define_outcome"
  }
}

/**
 * A deployment: a persisted, optionally-scheduled configuration that launches sessions for an
 * agent in an environment with a fixed set of initial events.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
 *   API</a>
 */
final case class Deployment(
  id: String,
  name: String,
  agent: AgentReference,
  environmentId: String,
  status: DeploymentStatus,
  initialEvents: Seq[DeploymentInitialEvent] = Nil,
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  resources: Seq[SessionResource] = Nil,
  schedule: Option[Schedule] = None,
  pausedReason: Option[DeploymentPausedReason] = None,
  vaultIds: Seq[String] = Nil,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "deployment"
}
