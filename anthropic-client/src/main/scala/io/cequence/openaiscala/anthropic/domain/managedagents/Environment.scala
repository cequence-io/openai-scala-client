package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/** Visibility scope for a (self-hosted) environment. */
sealed trait EnvironmentScope extends EnumValue

object EnvironmentScope {
  case object organization extends EnvironmentScope
  case object account extends EnvironmentScope

  def values: Seq[EnvironmentScope] = Seq(organization, account)
}

/** Network access policy for a cloud environment. */
sealed trait Networking

object Networking {

  /** Full outbound access. */
  case object Unrestricted extends Networking {
    val `type`: String = "unrestricted"
  }

  /**
   * Deny-by-default outbound access.
   *
   * @param allowMcpServers
   *   Permit the agent's configured MCP server endpoints (beyond `allowedHosts`).
   * @param allowPackageManagers
   *   Permit public package registries (PyPI, npm, …) beyond `allowedHosts`.
   * @param allowedHosts
   *   Domains the container can reach.
   */
  final case class Limited(
    allowMcpServers: Option[Boolean] = None,
    allowPackageManagers: Option[Boolean] = None,
    allowedHosts: Seq[String] = Nil
  ) extends Networking {
    val `type`: String = "limited"
  }
}

/**
 * Packages (and optional versions) preinstalled in a cloud environment. For versions use the
 * package manager's syntax, e.g. pip `package==1.0.0`.
 */
final case class Packages(
  apt: Seq[String] = Nil,
  cargo: Seq[String] = Nil,
  gem: Seq[String] = Nil,
  go: Seq[String] = Nil,
  npm: Seq[String] = Nil,
  pip: Seq[String] = Nil
)

/** Environment configuration — Anthropic cloud or self-hosted. */
sealed trait EnvironmentConfig

object EnvironmentConfig {

  /** Anthropic-hosted container. */
  final case class Cloud(
    networking: Option[Networking] = None,
    packages: Option[Packages] = None
  ) extends EnvironmentConfig {
    val `type`: String = "cloud"
  }

  /** Customer-hosted sandbox (worker polls Anthropic's work queue). */
  case object SelfHosted extends EnvironmentConfig {
    val `type`: String = "self_hosted"
  }
}

/**
 * A reusable container-provisioning template that sessions reference.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/environments">Anthropic Environments
 *   API</a>
 */
final case class Environment(
  id: String,
  name: String,
  config: EnvironmentConfig,
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  scope: Option[EnvironmentScope] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "environment"
}

/** Response from deleting an environment. */
final case class EnvironmentDeleteResponse(id: String) {
  val `type`: String = "environment_deleted"
}
