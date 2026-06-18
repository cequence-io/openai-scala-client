package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/** Lifecycle status of a session. */
sealed trait SessionStatus extends EnumValue

object SessionStatus {
  case object rescheduling extends SessionStatus
  case object running extends SessionStatus
  case object idle extends SessionStatus
  case object terminated extends SessionStatus

  def values: Seq[SessionStatus] = Seq(rescheduling, running, idle, terminated)
}

/** Read/write access for a mounted memory store. */
sealed trait MemoryStoreAccess extends EnumValue

object MemoryStoreAccess {
  case object read_write extends MemoryStoreAccess
  case object read_only extends MemoryStoreAccess

  def values: Seq[MemoryStoreAccess] = Seq(read_write, read_only)
}

/** Git checkout target for a mounted GitHub repository. */
sealed trait Checkout

object Checkout {
  final case class Branch(name: String) extends Checkout {
    val `type`: String = "branch"
  }
  final case class Commit(sha: String) extends Checkout {
    val `type`: String = "commit"
  }
}

/**
 * A resource attached to a session. Used both when adding (request — `github_repository`
 * carries `authorizationToken`) and as returned by the API (carries `id` + timestamps).
 */
sealed trait SessionResource

object SessionResource {

  final case class File(
    fileId: String,
    mountPath: Option[String] = None,
    id: Option[String] = None,
    createdAt: Option[String] = None,
    updatedAt: Option[String] = None
  ) extends SessionResource {
    val `type`: String = "file"
  }

  final case class GithubRepository(
    url: String,
    authorizationToken: Option[String] = None,
    checkout: Option[Checkout] = None,
    mountPath: Option[String] = None,
    id: Option[String] = None,
    createdAt: Option[String] = None,
    updatedAt: Option[String] = None
  ) extends SessionResource {
    val `type`: String = "github_repository"
  }

  final case class MemoryStore(
    memoryStoreId: String,
    access: Option[MemoryStoreAccess] = None,
    instructions: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    mountPath: Option[String] = None
  ) extends SessionResource {
    val `type`: String = "memory_store"
  }
}

/** Response from deleting a session. */
final case class SessionDeleteResponse(id: String) {
  val `type`: String = "session_deleted"
}

/**
 * A stateful interaction with a Managed Agent inside an environment.
 *
 * The session's `agent` is a resolved snapshot of the agent configuration (reusing [[Agent]];
 * its timestamp fields are absent here and read as `None`).
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions API</a>
 */
final case class Session(
  id: String,
  status: SessionStatus,
  agent: Agent,
  environmentId: String,
  title: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  resources: Seq[SessionResource] = Nil,
  vaultIds: Seq[String] = Nil,
  deploymentId: Option[String] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "session"
}
