package io.cequence.openaiscala.anthropic.service.auth

import io.cequence.openaiscala.anthropic.service.{
  AnthropicScalaClientException,
  AnthropicScalaUnauthorizedException
}
import play.api.libs.json._

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.util.control.NonFatal

/**
 * [[AnthropicTokenProvider]] backed by an `ant auth login` OAuth profile, mirroring the
 * credential-refresh behavior of the official Anthropic Python/TypeScript SDKs.
 *
 * On construction, the profile's config file (`<configDir>/configs/<profile>.json`, resolved
 * via [[AnthropicOAuthProfile.resolveConfigDir]] /
 * [[AnthropicOAuthProfile.resolveProfileName]]) is loaded eagerly - construction fails fast if
 * the profile's `authentication.type` is `"oidc_federation"` (OIDC federation is '''not yet
 * supported''') or any other unrecognized type. The profile's credentials file is then loaded
 * into a cached, volatile field.
 *
 * [[accessToken]] serves the cached token when it is comfortably valid, proactively (and
 * best-effort) refreshes it once it is within [[AnthropicOAuthConsts.advisoryRefreshSeconds]]
 * of expiry - swallowing failures and not retrying more often than every
 * [[AnthropicOAuthConsts.refreshFailureBackoffSeconds]] - and blocks the caller on a refresh
 * once within [[AnthropicOAuthConsts.mandatoryRefreshSeconds]] of expiry (or already expired),
 * throwing a [[AnthropicScalaUnauthorizedException]] if the token is still expired afterwards
 * (with no way left to refresh it). A refresh is a `POST` to `{config.baseUrl or
 * AnthropicOAuthConsts.defaultBaseUrl}{AnthropicOAuthConsts.tokenEndpointPath}` carrying the
 * `anthropic-beta: oauth-2025-04-20` header; when the profile has no `client_id` configured,
 * refreshing degrades to simply re-reading the credentials file, on the assumption that an
 * external process rotates it.
 *
 * All state transitions are single-flighted behind a private lock with double-checked reads so
 * that concurrent callers never stampede the refresh endpoint.
 *
 * @param configDir
 *   Overrides the resolved Anthropic config directory (see
 *   [[AnthropicOAuthProfile.resolveConfigDir]]). Defaults to env-var/OS-based resolution.
 * @param profile
 *   Overrides the resolved profile name (see [[AnthropicOAuthProfile.resolveProfileName]]).
 *   Defaults to env-var/`active_config`-based resolution.
 */
class AnthropicOAuthProfileTokenProvider(
  configDir: Option[Path] = None,
  profile: Option[String] = None
) extends AnthropicTokenProvider {

  private val resolvedConfigDir: Path = AnthropicOAuthProfile.resolveConfigDir(configDir)
  private val resolvedProfile: String =
    AnthropicOAuthProfile.resolveProfileName(resolvedConfigDir, profile)
  private val config: AnthropicOAuthProfileConfig =
    AnthropicOAuthProfile.loadConfig(resolvedConfigDir, resolvedProfile)
  private val credentialsPath: Path =
    AnthropicOAuthProfile.credentialsFilePath(resolvedConfigDir, resolvedProfile, config)

  // single-flights refresh/re-read attempts; all mutation of the volatile vars below happens
  // while holding this lock
  private val lock = new Object

  @volatile private var credentials: AnthropicOAuthCredentials =
    AnthropicOAuthProfile.loadCredentials(credentialsPath)

  @volatile private var lastRefreshFailureEpochSeconds: Option[Long] = None

  private val httpClient: HttpClient = HttpClient.newHttpClient()

  override def extraHeaders: Seq[(String, String)] =
    config.workspaceId
      .map(w => Seq((AnthropicOAuthConsts.workspaceIdHeaderName, w)))
      .getOrElse(Nil)

  /** Overridable for tests. */
  protected def nowSeconds(): Long = System.currentTimeMillis() / 1000

  /**
   * Performs the token-refresh HTTP call. Overridable for tests. The default implementation
   * uses the JDK's [[java.net.http.HttpClient]] synchronously and throws
   * [[AnthropicScalaClientException]] with the status code and body on a non-2xx response (or
   * on a transport failure).
   */
  protected def postRefresh(
    url: String,
    bodyJson: JsObject
  ): JsValue = {
    val request = HttpRequest
      .newBuilder(URI.create(url))
      .header("Content-Type", "application/json")
      .header("anthropic-beta", AnthropicOAuthConsts.oauthBetaValue)
      .POST(
        HttpRequest.BodyPublishers.ofString(Json.stringify(bodyJson), StandardCharsets.UTF_8)
      )
      .build()

    val response =
      try {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      } catch {
        case NonFatal(e) =>
          throw new AnthropicScalaClientException(
            s"Anthropic OAuth token refresh request to '$url' failed: ${e.getMessage}",
            e
          )
      }

    if (response.statusCode() / 100 != 2) {
      throw new AnthropicScalaClientException(
        s"Anthropic OAuth token refresh failed with status ${response.statusCode()}: " +
          response.body()
      )
    }

    Json.parse(response.body())
  }

  def accessToken(): String = {
    val snapshot = credentials
    val remaining = snapshot.expiresAt.map(_ - nowSeconds())

    remaining match {
      // no expiry tracked: either externally-rotated (re-read every call) or a
      // never-expiring / long-lived token (just trust the cache)
      case None =>
        if (config.clientId.isEmpty) {
          credentials = AnthropicOAuthProfile.loadCredentials(credentialsPath)
        }
        credentials.accessToken

      // comfortably valid
      case Some(r) if r > AnthropicOAuthConsts.advisoryRefreshSeconds =>
        snapshot.accessToken

      // advisory window: best-effort, non-blocking-ish refresh; always serve the cached
      // (still valid) token regardless of outcome
      case Some(r) if r > AnthropicOAuthConsts.mandatoryRefreshSeconds =>
        attemptAdvisoryRefresh()
        credentials.accessToken

      // mandatory window (or already expired): block on a refresh, or die
      case Some(_) =>
        lock.synchronized {
          ensureFreshOrDie()
        }
        credentials.accessToken
    }
  }

  private def canHttpRefresh: Boolean =
    config.clientId.isDefined && credentials.refreshToken.isDefined

  private def recentlyFailed(): Boolean =
    lastRefreshFailureEpochSeconds.exists { t =>
      nowSeconds() - t < AnthropicOAuthConsts.refreshFailureBackoffSeconds
    }

  /**
   * Refreshes the cached credentials: performs an HTTP refresh when possible ([[config]] has a
   * `client_id` and the cached credentials have a `refresh_token`), otherwise re-reads the
   * credentials file (externally-rotated mode). Throws on failure. Callers must hold [[lock]].
   */
  private def refreshOrReread(): Unit =
    if (canHttpRefresh) {
      refreshViaHttp()
    } else {
      credentials = AnthropicOAuthProfile.loadCredentials(credentialsPath)
    }

  private def attemptAdvisoryRefresh(): Unit =
    if (!recentlyFailed()) {
      lock.synchronized {
        // double-checked: another thread may have already refreshed (or just failed) while
        // we were waiting for the lock
        val remaining = credentials.expiresAt.map(_ - nowSeconds())
        val stillDue = remaining.exists(_ <= AnthropicOAuthConsts.advisoryRefreshSeconds)

        if (stillDue && !recentlyFailed()) {
          try {
            refreshOrReread()
            lastRefreshFailureEpochSeconds = None
          } catch {
            case NonFatal(_) =>
              // swallow: keep serving the still-valid cached token, but remember the
              // failure so we don't hammer the endpoint on every subsequent call
              lastRefreshFailureEpochSeconds = Some(nowSeconds())
          }
        }
      }
    }

  /** Callers must hold [[lock]]. */
  private def ensureFreshOrDie(): Unit = {
    // double-checked: another thread may have already refreshed while we waited for the lock
    val remaining = credentials.expiresAt.map(_ - nowSeconds())
    val stillDue = !remaining.exists(_ > AnthropicOAuthConsts.mandatoryRefreshSeconds)

    if (stillDue) {
      if (canHttpRefresh) {
        refreshViaHttp() // failure propagates as-is: this *is* the "die" path
      } else {
        // refresh impossible (no client_id and/or no refresh_token): an external process may
        // have rotated the credentials file in the meantime
        credentials = AnthropicOAuthProfile.loadCredentials(credentialsPath)

        val remainingAfterReread = credentials.expiresAt.map(_ - nowSeconds())
        if (remainingAfterReread.exists(_ <= 0)) {
          throw new AnthropicScalaUnauthorizedException(
            "Anthropic OAuth access token has expired and cannot be refreshed (no client_id " +
              s"and/or refresh_token configured for profile '$resolvedProfile'). " +
              "Please re-run 'ant auth login'."
          )
        }
      }
    }
  }

  /** Requires [[canHttpRefresh]]. Callers must hold [[lock]]. */
  private def refreshViaHttp(): Unit = {
    val clientId = config.clientId.getOrElse(
      throw new IllegalStateException(
        "Cannot refresh Anthropic OAuth token: no client_id configured"
      )
    )
    val refreshToken = credentials.refreshToken.getOrElse(
      throw new IllegalStateException(
        "Cannot refresh Anthropic OAuth token: no refresh_token available"
      )
    )

    val baseUrl = config.baseUrl.getOrElse(AnthropicOAuthConsts.defaultBaseUrl)
    val url = s"$baseUrl${AnthropicOAuthConsts.tokenEndpointPath}"

    val requestBody = Json.obj(
      "grant_type" -> AnthropicOAuthConsts.grantTypeRefreshToken,
      "refresh_token" -> refreshToken,
      "client_id" -> clientId
    )

    val responseJson = postRefresh(url, requestBody)

    val newAccessToken = (responseJson \ "access_token")
      .asOpt[String]
      .getOrElse(
        throw new AnthropicScalaClientException(
          "Anthropic OAuth token refresh response is missing 'access_token': " +
            Json.stringify(responseJson)
        )
      )
    val expiresIn = (responseJson \ "expires_in")
      .asOpt[Long]
      .getOrElse(AnthropicOAuthConsts.defaultExpiresInSeconds.toLong)
    val rotatedRefreshToken =
      (responseJson \ "refresh_token").asOpt[String].orElse(credentials.refreshToken)
    val rotatedScope = (responseJson \ "scope").asOpt[String].orElse(credentials.scope)

    // preserves organization_uuid / organization_name / account_email from the previous
    // credentials, since copy() only touches the fields set below
    val updated = credentials.copy(
      accessToken = newAccessToken,
      refreshToken = rotatedRefreshToken,
      expiresAt = Some(nowSeconds() + expiresIn),
      scope = rotatedScope
    )

    credentials = updated
    AnthropicOAuthProfile.writeCredentialsAtomic(credentialsPath, updated)
  }
}
