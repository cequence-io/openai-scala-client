package io.cequence.openaiscala.anthropic.domain.managedagents

import play.api.libs.json.JsObject

/** How the OAuth token endpoint authenticates a refresh request. */
sealed trait TokenEndpointAuth

object TokenEndpointAuth {

  /** Public client, no secret. */
  case object None_ extends TokenEndpointAuth {
    val `type`: String = "none"
  }

  /** Confidential client, secret via HTTP Basic auth. */
  final case class ClientSecretBasic(clientSecret: String) extends TokenEndpointAuth {
    val `type`: String = "client_secret_basic"
  }

  /** Confidential client, secret in the request body. */
  final case class ClientSecretPost(clientSecret: String) extends TokenEndpointAuth {
    val `type`: String = "client_secret_post"
  }
}

/** OAuth refresh-token configuration for an MCP OAuth credential (create side). */
final case class McpOAuthRefresh(
  clientId: String,
  refreshToken: String,
  tokenEndpoint: String,
  tokenEndpointAuth: TokenEndpointAuth,
  scope: Option[String] = None
)

/** Network access policy for an environment-variable credential. */
sealed trait CredentialNetworking

object CredentialNetworking {
  case object Unrestricted extends CredentialNetworking {
    val `type`: String = "unrestricted"
  }
  final case class Limited(allowedHosts: Seq[String] = Nil) extends CredentialNetworking {
    val `type`: String = "limited"
  }
}

/**
 * Credential auth material (create side). Secrets are write-only — they are never returned in
 * credential responses.
 */
sealed trait CredentialAuth

object CredentialAuth {

  /** MCP OAuth credential with optional auto-refresh. */
  final case class McpOAuth(
    accessToken: String,
    mcpServerUrl: String,
    expiresAt: Option[String] = None,
    refresh: Option[McpOAuthRefresh] = None
  ) extends CredentialAuth {
    val `type`: String = "mcp_oauth"
  }

  /** Static bearer token for an MCP server. */
  final case class StaticBearer(
    token: String,
    mcpServerUrl: String
  ) extends CredentialAuth {
    val `type`: String = "static_bearer"
  }

  /** A named secret exposed as an environment variable to outbound requests. */
  final case class EnvironmentVariable(
    secretName: String,
    secretValue: String,
    networking: CredentialNetworking
  ) extends CredentialAuth {
    val `type`: String = "environment_variable"
  }
}

/**
 * A credential stored in a vault. Responses omit all secret material; the auth discriminator
 * and non-secret fields are typed, and the full payload is retained in [[raw]].
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/vaults/credentials">Anthropic
 *   Credentials API</a>
 */
final case class Credential(
  id: String,
  authType: String,
  displayName: Option[String] = None,
  mcpServerUrl: Option[String] = None,
  expiresAt: Option[String] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None,
  raw: JsObject = JsObject.empty
)
