package io.cequence.openaiscala.anthropic.service.auth

/**
 * Supplies a bearer access token (and any auth-related extra headers) for Anthropic requests
 * authenticated via OAuth rather than a static `x-api-key`. Implementations are expected to be
 * thread-safe - [[accessToken]] may be called concurrently from many request threads.
 */
trait AnthropicTokenProvider {

  /**
   * Returns a currently-valid access token. May block briefly while a refresh is in flight.
   */
  def accessToken(): String

  /** Additional per-request headers (e.g. anthropic-workspace-id). */
  def extraHeaders: Seq[(String, String)] = Nil
}

object AnthropicTokenProvider {

  /** A [[AnthropicTokenProvider]] that always returns the same, never-refreshed token. */
  def static(token: String): AnthropicTokenProvider =
    new AnthropicTokenProvider {
      def accessToken(): String = token
    }
}

/**
 * Shared constants for the Anthropic OAuth/bearer auth subsystem. Values (endpoint path, beta
 * header, refresh windows, file-format markers, env var names) mirror those used by the
 * official Anthropic Python/TypeScript SDKs' `ant auth login` credential handling, so that
 * profiles created by the CLI can be consumed as-is.
 */
object AnthropicOAuthConsts {

  /** Value of the `anthropic-beta` header sent on OAuth-authenticated requests. */
  val oauthBetaValue = "oauth-2025-04-20"

  /** Path (relative to the profile's base url) of the OAuth token-refresh endpoint. */
  val tokenEndpointPath = "/v1/oauth/token"

  /** `grant_type` value used when refreshing an access token. */
  val grantTypeRefreshToken = "refresh_token"

  /** Base url used when a profile config does not specify its own `base_url`. */
  val defaultBaseUrl = "https://api.anthropic.com"

  /**
   * Proactively refresh once the access token is within this many seconds of expiry. Refresh
   * failures in this window are swallowed and the (still valid) cached token is served.
   */
  val advisoryRefreshSeconds = 120

  /**
   * Block the caller on a refresh once the access token is within this many seconds of expiry
   * (or already expired). A refresh failure here is fatal.
   */
  val mandatoryRefreshSeconds = 30

  /** Don't re-attempt an advisory refresh within this many seconds of a previous failure. */
  val refreshFailureBackoffSeconds = 5

  /** Assumed token lifetime, in seconds, when a refresh response omits `expires_in`. */
  val defaultExpiresInSeconds = 3600

  /** Header carrying the workspace id for profiles scoped to a specific workspace. */
  val workspaceIdHeaderName = "anthropic-workspace-id"

  /** Expected value of the credentials file's `type` field. */
  val credentialsFileType = "oauth_token"

  /** Env var overriding the resolved Anthropic config directory. */
  val envConfigDir = "ANTHROPIC_CONFIG_DIR"

  /** Env var overriding the resolved active profile name. */
  val envProfile = "ANTHROPIC_PROFILE"
}
