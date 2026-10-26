package io.cequence.openaiscala.anthropic.service.auth

import io.cequence.openaiscala.anthropic.service.{
  AnthropicScalaClientException,
  AnthropicScalaClientTimeoutException,
  AnthropicScalaUnauthorizedException
}
import play.api.libs.json._

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, HttpTimeoutException}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.{ExecutionException, TimeUnit, TimeoutException}
import java.util.concurrent.locks.ReentrantLock
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
 * Refreshes are single-flighted behind a private [[java.util.concurrent.locks.ReentrantLock]]
 * with double-checked reads so that concurrent callers never stampede the refresh endpoint,
 * and every refresh is bounded in time, so a token endpoint that accepts the connection and
 * never answers cannot hang the service:
 *   - the refresh `POST` has a connect timeout and a deadline on the WHOLE exchange (response
 *     body included); when it expires the request is cancelled and the attempt fails with an
 *     [[AnthropicScalaClientTimeoutException]], which in the advisory window engages the
 *     failure backoff;
 *   - in the advisory window a caller never waits for a refresh that is already in flight: it
 *     returns the cached (still valid) token at once; only the caller that got the lock waits
 *     for its own refresh, at most `refreshTimeout`;
 *   - in the mandatory window (or when expired) a caller waits for an in-flight refresh at
 *     most `lockWaitTimeout`, then fails with an [[AnthropicScalaClientException]]; a caller
 *     that gets the lock refreshes on its own, again bounded by `refreshTimeout`.
 *
 * A thread interrupted while it waits or refreshes keeps its interrupt status: the advisory
 * path serves the cached token, the mandatory one throws an [[AnthropicScalaClientException]].
 *
 * @param configDir
 *   Overrides the resolved Anthropic config directory (see
 *   [[AnthropicOAuthProfile.resolveConfigDir]]). Defaults to env-var/OS-based resolution.
 * @param profile
 *   Overrides the resolved profile name (see [[AnthropicOAuthProfile.resolveProfileName]]).
 *   Defaults to env-var/`active_config`-based resolution.
 * @param connectTimeout
 *   Connect timeout of the token-refresh HTTP client (default 10 s).
 * @param refreshTimeout
 *   Deadline of one whole token-refresh exchange, request to last response byte (default 30
 *   s).
 * @param lockWaitTimeout
 *   How long a mandatory-window caller waits for a refresh that is already in flight before it
 *   fails (default 45 s).
 */
class AnthropicOAuthProfileTokenProvider(
  configDir: Option[Path] = None,
  profile: Option[String] = None,
  connectTimeout: Duration = AnthropicOAuthProfileTokenProvider.defaultConnectTimeout,
  refreshTimeout: Duration = AnthropicOAuthProfileTokenProvider.defaultRefreshTimeout,
  lockWaitTimeout: Duration = AnthropicOAuthProfileTokenProvider.defaultLockWaitTimeout
) extends AnthropicTokenProvider {

  require(
    !connectTimeout.isNegative && !connectTimeout.isZero,
    s"connectTimeout must be positive, got $connectTimeout"
  )
  require(
    !refreshTimeout.isNegative && !refreshTimeout.isZero,
    s"refreshTimeout must be positive, got $refreshTimeout"
  )
  require(
    !lockWaitTimeout.isNegative,
    s"lockWaitTimeout must not be negative, got $lockWaitTimeout"
  )

  private val resolvedConfigDir: Path = AnthropicOAuthProfile.resolveConfigDir(configDir)
  private val resolvedProfile: String =
    AnthropicOAuthProfile.resolveProfileName(resolvedConfigDir, profile)
  private val config: AnthropicOAuthProfileConfig =
    AnthropicOAuthProfile.loadConfig(resolvedConfigDir, resolvedProfile)
  private val credentialsPath: Path =
    AnthropicOAuthProfile.credentialsFilePath(resolvedConfigDir, resolvedProfile, config)

  // single-flights refresh/re-read attempts; all mutation of the volatile vars below happens
  // while holding this lock. Never waited for without a bound (see tryLock below).
  private val lock = new ReentrantLock()

  @volatile private var credentials: AnthropicOAuthCredentials =
    AnthropicOAuthProfile.loadCredentials(credentialsPath)

  @volatile private var lastRefreshFailureEpochSeconds: Option[Long] = None

  private val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(connectTimeout).build()

  override def extraHeaders: Seq[(String, String)] =
    config.workspaceId
      .map(w => Seq((AnthropicOAuthConsts.workspaceIdHeaderName, w)))
      .getOrElse(Nil)

  /** Overridable for tests. */
  protected def nowSeconds(): Long = System.currentTimeMillis() / 1000

  /**
   * Performs the token-refresh HTTP call. Overridable for tests. The default implementation
   * uses the JDK's [[java.net.http.HttpClient]] and throws [[AnthropicScalaClientException]]
   * with the status code and body on a non-2xx response (or on a transport failure), and an
   * [[AnthropicScalaClientTimeoutException]] when the exchange does not complete within
   * `refreshTimeout`. An overriding implementation must bound its own run time - the caller
   * holds the refresh lock meanwhile.
   */
  protected def postRefresh(
    url: String,
    bodyJson: JsObject
  ): JsValue = {
    val request = HttpRequest
      .newBuilder(URI.create(url))
      // ends a stall before the response headers; one after them (e.g. a stalled body) is
      // not covered, hence the deadline on the whole exchange below
      .timeout(refreshTimeout)
      .header("Content-Type", "application/json")
      .header("anthropic-beta", AnthropicOAuthConsts.oauthBetaValue)
      .POST(
        HttpRequest.BodyPublishers.ofString(Json.stringify(bodyJson), StandardCharsets.UTF_8)
      )
      .build()

    val pending =
      httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

    val response =
      try {
        pending.get(refreshTimeout.toNanos, TimeUnit.NANOSECONDS)
      } catch {
        case e: TimeoutException =>
          // closes the connection, so the next attempt starts on a fresh one
          pending.cancel(true)
          throw new AnthropicScalaClientTimeoutException(
            s"Anthropic OAuth token refresh request to '$url' timed out after " +
              s"${refreshTimeout.toMillis} ms",
            e
          )

        case e: InterruptedException =>
          pending.cancel(true)
          // not rethrown (accessToken cannot declare it): the status is kept for the caller
          Thread.currentThread().interrupt()
          throw new AnthropicScalaClientException(
            s"Anthropic OAuth token refresh request to '$url' was interrupted",
            e
          )

        case e: ExecutionException =>
          throw refreshFailure(url, Option(e.getCause).getOrElse(e))

        case NonFatal(e) =>
          pending.cancel(true)
          throw refreshFailure(url, e)
      }

    if (response.statusCode() / 100 != 2) {
      throw new AnthropicScalaClientException(
        s"Anthropic OAuth token refresh failed with status ${response.statusCode()}: " +
          response.body()
      )
    }

    Json.parse(response.body())
  }

  private def refreshFailure(
    url: String,
    cause: Throwable
  ): AnthropicScalaClientException = {
    val reason = Option(cause.getMessage).getOrElse(cause.getClass.getName)

    cause match {
      case _: HttpTimeoutException =>
        new AnthropicScalaClientTimeoutException(
          s"Anthropic OAuth token refresh request to '$url' timed out: $reason",
          cause
        )

      case _ =>
        new AnthropicScalaClientException(
          s"Anthropic OAuth token refresh request to '$url' failed: $reason",
          cause
        )
    }
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

      // advisory window: best-effort refresh that never waits for one already in flight (a
      // caller that does not get the lock returns at once); always serve the cached (still
      // valid) token regardless of outcome
      case Some(r) if r > AnthropicOAuthConsts.mandatoryRefreshSeconds =>
        attemptAdvisoryRefresh()
        credentials.accessToken

      // mandatory window (or already expired): wait for an in-flight refresh (bounded) and
      // refresh, or die
      case Some(_) =>
        acquireLockOrFail()

        try {
          ensureFreshOrDie()
        } finally {
          lock.unlock()
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

  /**
   * Never waits: a caller that finds a refresh in flight (the lock taken) returns at once and
   * serves the cached token.
   */
  private def attemptAdvisoryRefresh(): Unit =
    if (!recentlyFailed() && lock.tryLock()) {
      try {
        // double-checked: another thread may have already refreshed (or just failed) between
        // our check and getting the lock
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
      } finally {
        lock.unlock()
      }
    }

  /**
   * Waits for [[lock]] at most `lockWaitTimeout`; fails with an
   * [[AnthropicScalaClientException]] when it is not free by then (a refresh by another thread
   * is stalled) or the thread is interrupted (its interrupt status is kept). The caller must
   * `unlock()` in a `finally` when this returns.
   */
  private def acquireLockOrFail(): Unit = {
    val acquired =
      try {
        lock.tryLock(lockWaitTimeout.toNanos, TimeUnit.NANOSECONDS)
      } catch {
        case e: InterruptedException =>
          Thread.currentThread().interrupt()
          throw new AnthropicScalaClientException(
            "Interrupted while waiting for an in-flight Anthropic OAuth token refresh",
            e
          )
      }

    if (!acquired) {
      throw new AnthropicScalaClientException(
        s"Timed out after ${lockWaitTimeout.toMillis} ms waiting for an in-flight " +
          "Anthropic OAuth token refresh"
      )
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

object AnthropicOAuthProfileTokenProvider {

  /** Default connect timeout of the token-refresh HTTP client. */
  val defaultConnectTimeout: Duration = Duration.ofSeconds(10)

  /** Default deadline of one whole token-refresh exchange. */
  val defaultRefreshTimeout: Duration = Duration.ofSeconds(30)

  /** Default time a mandatory-window caller waits for an in-flight refresh. */
  val defaultLockWaitTimeout: Duration = Duration.ofSeconds(45)
}
