package io.cequence.openaiscala.anthropic.service.auth

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsValue, Json}

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermission}
import java.nio.file.{Files, Path, Paths}

/**
 * Unit tests for the `ant auth`-style OAuth profile plumbing:
 *   - [[AnthropicOAuthProfile]] — config/credentials file parsing, path resolution, atomic
 *     credentials write
 *   - [[AnthropicOAuthProfileTokenProvider]] — cached-token / advisory-refresh /
 *     mandatory-refresh-or-die logic
 *
 * Wire-format note: the exact JSON layout of `configs/<profile>.json` isn't pinned anywhere
 * outside prose, so this spec assumes the natural reading of that prose — `type`, `client_id`,
 * `scope` and `credentials_path` nested under an `"authentication"` object (mirrors the
 * `authentication.type` / `authentication.credentials_path` dotted references), with
 * `base_url` / `organization_id` / `workspace_id` as top-level siblings (mirrors
 * `workspace_id` being referenced *without* an `authentication.` prefix). If the real
 * implementation lays fields out differently, the `loadConfig` tests below are the ones to
 * reconcile first — the credentials file layout (flat, snake_case) and the refresh
 * request/response shapes are spelled out explicitly and are not guesses.
 */
class AnthropicOAuthProfileSpec extends AnyWordSpec with Matchers {

  // ---------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------

  private val fullDetailedConfigJson =
    """{
      |  "authentication": {
      |    "type": "user_oauth",
      |    "client_id": "client-abc",
      |    "scope": "user:inference",
      |    "credentials_path": "/custom/path/creds.json"
      |  },
      |  "base_url": "https://custom.anthropic.example",
      |  "organization_id": "org-123",
      |  "workspace_id": "ws-456"
      |}""".stripMargin

  private val minimalConfigJson =
    """{"authentication": {"type": "user_oauth"}}"""

  private val oidcFederationConfigJson =
    """{"authentication": {"type": "oidc_federation"}}"""

  // config used by the token-provider tests: has a client_id, so refresh is possible
  private val configWithClientIdJson =
    """{"authentication": {"type": "user_oauth", "client_id": "client-abc", "scope": "user:inference"}}"""

  // config used by the "no client_id" provider tests: refresh must not be attempted
  private val configWithoutClientIdJson =
    """{"authentication": {"type": "user_oauth"}}"""

  // config used by the workspace_id provider test
  private val configWithWorkspaceJson =
    """{"authentication": {"type": "user_oauth", "client_id": "client-abc"}, "workspace_id": "ws-789"}"""

  private def credentialsJson(
    accessToken: String,
    expiresAt: Long,
    refreshToken: String
  ): String =
    s"""{
       |  "type": "oauth_token",
       |  "access_token": "$accessToken",
       |  "refresh_token": "$refreshToken",
       |  "expires_at": $expiresAt,
       |  "scope": "user:inference"
       |}""".stripMargin

  // ---------------------------------------------------------------------------------------
  // Filesystem test helpers
  // ---------------------------------------------------------------------------------------

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        val children = Files.list(path)
        try children.forEach(child => deleteRecursively(child))
        finally children.close()
      }
      Files.deleteIfExists(path)
      ()
    }

  /**
   * Creates a fresh temp "config dir" and writes `configs/<profile>.json` (and, if provided,
   * `credentials/<profile>.json`) into it, then runs `test`, then deletes the whole tree.
   */
  private def withTempProfile(
    configJson: String,
    credentialsJson: Option[String] = None,
    profile: String = "default"
  )(
    test: (Path, String) => Any
  ): Unit = {
    val configDir = Files.createTempDirectory("anthropic-oauth-profile-spec")
    try {
      val configsDir = Files.createDirectories(configDir.resolve("configs"))
      Files.write(
        configsDir.resolve(s"$profile.json"),
        configJson.getBytes(StandardCharsets.UTF_8)
      )

      credentialsJson.foreach { json =>
        val credentialsDir = Files.createDirectories(configDir.resolve("credentials"))
        Files.write(
          credentialsDir.resolve(s"$profile.json"),
          json.getBytes(StandardCharsets.UTF_8)
        )
      }

      test(configDir, profile)
      ()
    } finally {
      deleteRecursively(configDir)
    }
  }

  private def withTempDir(test: Path => Any): Unit = {
    val dir = Files.createTempDirectory("anthropic-oauth-profile-spec")
    try {
      test(dir)
      ()
    } finally {
      deleteRecursively(dir)
    }
  }

  private val failingRefresh: (String, JsObject) => JsValue = (
    _,
    _
  ) => throw new AssertionError("postRefresh should not have been called")

  /** Test double exposing the protected hooks as constructor-supplied behaviour. */
  private class TestProvider(
    configDir: Path,
    profile: String,
    fixedNow: Long,
    refreshBehavior: (String, JsObject) => JsValue
  ) extends AnthropicOAuthProfileTokenProvider(Some(configDir), Some(profile)) {

    var refreshCallCount: Int = 0
    var lastRefreshUrl: Option[String] = None
    var lastRefreshBody: Option[JsObject] = None

    override protected def nowSeconds(): Long = fixedNow

    override protected def postRefresh(
      url: String,
      bodyJson: JsObject
    ): JsValue = {
      refreshCallCount += 1
      lastRefreshUrl = Some(url)
      lastRefreshBody = Some(bodyJson)
      refreshBehavior(url, bodyJson)
    }
  }

  // a fixed "current time" used throughout the provider tests (arbitrary epoch seconds)
  private val now = 1700000000L

  // ---------------------------------------------------------------------------------------
  // Config parsing
  // ---------------------------------------------------------------------------------------

  "loadConfig" should {

    "parse a full user_oauth config with client_id/scope/base_url/organization_id/workspace_id" in {
      withTempProfile(configJson = fullDetailedConfigJson) {
        (
          configDir,
          profile
        ) =>
          val config = AnthropicOAuthProfile.loadConfig(configDir, profile)

          config shouldBe AnthropicOAuthProfileConfig(
            authType = "user_oauth",
            clientId = Some("client-abc"),
            scope = Some("user:inference"),
            credentialsPath = Some("/custom/path/creds.json"),
            baseUrl = Some("https://custom.anthropic.example"),
            organizationId = Some("org-123"),
            workspaceId = Some("ws-456")
          )
      }
    }

    "parse a minimal config with only authentication.type, leaving the rest as None" in {
      withTempProfile(configJson = minimalConfigJson) {
        (
          configDir,
          profile
        ) =>
          val config = AnthropicOAuthProfile.loadConfig(configDir, profile)

          config shouldBe AnthropicOAuthProfileConfig(
            authType = "user_oauth",
            clientId = None,
            scope = None,
            credentialsPath = None,
            baseUrl = None,
            organizationId = None,
            workspaceId = None
          )
      }
    }

    "throw for an oidc_federation authentication type" in {
      withTempProfile(configJson = oidcFederationConfigJson) {
        (
          configDir,
          profile
        ) =>
          intercept[Exception] {
            AnthropicOAuthProfile.loadConfig(configDir, profile)
          }
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // Credentials parsing
  // ---------------------------------------------------------------------------------------

  "loadCredentials" should {

    "round-trip a full credentials file (type, access_token, refresh_token, expires_at, scope, organization_uuid)" in {
      withTempDir { dir =>
        val path = dir.resolve("creds.json")
        val json =
          """{
            |  "type": "oauth_token",
            |  "access_token": "at-full",
            |  "refresh_token": "rt-full",
            |  "expires_at": 1750000000,
            |  "scope": "user:inference",
            |  "organization_uuid": "org-uuid-1"
            |}""".stripMargin
        Files.write(path, json.getBytes(StandardCharsets.UTF_8))

        val creds = AnthropicOAuthProfile.loadCredentials(path)

        creds shouldBe AnthropicOAuthCredentials(
          accessToken = "at-full",
          refreshToken = Some("rt-full"),
          expiresAt = Some(1750000000L),
          scope = Some("user:inference"),
          organizationUuid = Some("org-uuid-1"),
          organizationName = None,
          accountEmail = None
        )
      }
    }

    "leave expiresAt as None when expires_at is absent" in {
      withTempDir { dir =>
        val path = dir.resolve("creds.json")
        Files.write(
          path,
          """{"type": "oauth_token", "access_token": "at-no-expiry"}""".getBytes(
            StandardCharsets.UTF_8
          )
        )

        val creds = AnthropicOAuthProfile.loadCredentials(path)

        creds.accessToken shouldBe "at-no-expiry"
        creds.expiresAt shouldBe None
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // Profile-name resolution
  // ---------------------------------------------------------------------------------------

  "resolveProfileName" should {

    "return the explicit override regardless of directory contents" in {
      withTempDir { dir =>
        AnthropicOAuthProfile.resolveProfileName(dir, Some("explicit-profile")) shouldBe
          "explicit-profile"
      }
    }

    "fall back to \"default\" when there is no override and no active_config file" in {
      withTempDir { dir =>
        AnthropicOAuthProfile.resolveProfileName(dir, None) shouldBe "default"
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // credentialsFilePath
  // ---------------------------------------------------------------------------------------

  "credentialsFilePath" should {

    "honor authentication.credentials_path when present" in {
      val configDir = Paths.get("some-config-dir")
      val config = AnthropicOAuthProfileConfig(
        authType = "user_oauth",
        clientId = None,
        scope = None,
        credentialsPath = Some("/custom/path/creds.json"),
        baseUrl = None,
        organizationId = None,
        workspaceId = None
      )

      AnthropicOAuthProfile.credentialsFilePath(configDir, "default", config) shouldBe
        Paths.get("/custom/path/creds.json")
    }

    "default to <configDir>/credentials/<profile>.json when not set" in {
      val configDir = Paths.get("some-config-dir")
      val config = AnthropicOAuthProfileConfig(
        authType = "user_oauth",
        clientId = None,
        scope = None,
        credentialsPath = None,
        baseUrl = None,
        organizationId = None,
        workspaceId = None
      )

      AnthropicOAuthProfile.credentialsFilePath(configDir, "myprofile", config) shouldBe
        configDir.resolve("credentials").resolve("myprofile.json")
    }
  }

  // ---------------------------------------------------------------------------------------
  // writeCredentialsAtomic
  // ---------------------------------------------------------------------------------------

  "writeCredentialsAtomic" should {

    "write a file loadCredentials can read back identically, with owner-only perms on POSIX" in {
      withTempDir { dir =>
        val path = dir.resolve("credentials").resolve("default.json")
        Files.createDirectories(path.getParent)

        val credentials = AnthropicOAuthCredentials(
          accessToken = "at-1",
          refreshToken = Some("rt-1"),
          expiresAt = Some(1999999999L),
          scope = Some("user:inference"),
          organizationUuid = Some("org-uuid"),
          organizationName = Some("Acme"),
          accountEmail = Some("user@example.com")
        )

        AnthropicOAuthProfile.writeCredentialsAtomic(path, credentials)

        val reread = AnthropicOAuthProfile.loadCredentials(path)
        reread shouldBe credentials

        val supportsPosix =
          Files.getFileStore(path).supportsFileAttributeView(classOf[PosixFileAttributeView])

        if (supportsPosix) {
          val perms = Files.getPosixFilePermissions(path)
          perms.contains(PosixFilePermission.GROUP_READ) shouldBe false
          perms.contains(PosixFilePermission.GROUP_WRITE) shouldBe false
          perms.contains(PosixFilePermission.GROUP_EXECUTE) shouldBe false
          perms.contains(PosixFilePermission.OTHERS_READ) shouldBe false
          perms.contains(PosixFilePermission.OTHERS_WRITE) shouldBe false
          perms.contains(PosixFilePermission.OTHERS_EXECUTE) shouldBe false
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // AnthropicOAuthProfileTokenProvider
  // ---------------------------------------------------------------------------------------

  "AnthropicOAuthProfileTokenProvider" should {

    "return the cached token without refreshing when far from expiry" in {
      withTempProfile(
        configJson = configWithClientIdJson,
        credentialsJson = Some(
          credentialsJson(
            accessToken = "at-fresh",
            expiresAt = now + 10000,
            refreshToken = "rt-fresh"
          )
        )
      ) {
        (
          configDir,
          profile
        ) =>
          val provider = new TestProvider(configDir, profile, now, failingRefresh)

          provider.accessToken() shouldBe "at-fresh"
          provider.refreshCallCount shouldBe 0
      }
    }

    "refresh in the advisory window and persist the rotated access + refresh token" in {
      withTempProfile(
        configJson = configWithClientIdJson,
        credentialsJson = Some(
          credentialsJson(
            accessToken = "at-old",
            expiresAt = now + 100,
            refreshToken = "rt-old"
          )
        )
      ) {
        (
          configDir,
          profile
        ) =>
          val provider = new TestProvider(
            configDir,
            profile,
            now,
            refreshBehavior = (
              _,
              _
            ) =>
              Json.obj(
                "access_token" -> "at-new",
                "expires_in" -> 3600,
                "refresh_token" -> "rt-new"
              )
          )

          provider.accessToken() shouldBe "at-new"
          provider.refreshCallCount shouldBe 1
          provider.lastRefreshUrl shouldBe
            Some(AnthropicOAuthConsts.defaultBaseUrl + AnthropicOAuthConsts.tokenEndpointPath)

          val body = provider.lastRefreshBody.get
          (body \ "grant_type").as[String] shouldBe AnthropicOAuthConsts.grantTypeRefreshToken
          (body \ "refresh_token").as[String] shouldBe "rt-old"
          (body \ "client_id").as[String] shouldBe "client-abc"

          // the on-disk credentials file must reflect the new access + rotated refresh token
          val credsPath = configDir.resolve("credentials").resolve(s"$profile.json")
          val onDisk = AnthropicOAuthProfile.loadCredentials(credsPath)
          onDisk.accessToken shouldBe "at-new"
          onDisk.refreshToken shouldBe Some("rt-new")
      }
    }

    "serve the stale cached token when a refresh in the advisory window fails" in {
      withTempProfile(
        configJson = configWithClientIdJson,
        credentialsJson = Some(
          credentialsJson(
            accessToken = "at-stale-ok",
            expiresAt = now + 100,
            refreshToken = "rt-old"
          )
        )
      ) {
        (
          configDir,
          profile
        ) =>
          val provider = new TestProvider(
            configDir,
            profile,
            now,
            refreshBehavior = (
              _,
              _
            ) => throw new RuntimeException("network down")
          )

          provider.accessToken() shouldBe "at-stale-ok"
      }
    }

    "when there is no client_id, re-read the credentials file from disk instead of refreshing" in {
      withTempProfile(
        configJson = configWithoutClientIdJson,
        credentialsJson = Some(
          credentialsJson(
            accessToken = "at-expired",
            expiresAt = now - 10,
            refreshToken = "rt-old"
          )
        )
      ) {
        (
          configDir,
          profile
        ) =>
          val provider = new TestProvider(configDir, profile, now, failingRefresh)

          // simulate an external process (e.g. `ant auth login`) rewriting the credentials file
          val credsPath = configDir.resolve("credentials").resolve(s"$profile.json")
          Files.write(
            credsPath,
            credentialsJson(
              accessToken = "at-rewritten",
              expiresAt = now + 10000,
              refreshToken = "rt-new"
            ).getBytes(StandardCharsets.UTF_8)
          )

          provider.accessToken() shouldBe "at-rewritten"
          provider.refreshCallCount shouldBe 0
      }
    }

    "when there is no client_id and the re-read token is still expired, throw mentioning re-login" in {
      withTempProfile(
        configJson = configWithoutClientIdJson,
        credentialsJson = Some(
          credentialsJson(
            accessToken = "at-expired",
            expiresAt = now - 10,
            refreshToken = "rt-old"
          )
        )
      ) {
        (
          configDir,
          profile
        ) =>
          val provider = new TestProvider(configDir, profile, now, failingRefresh)

          val ex = intercept[Exception] {
            provider.accessToken()
          }
          ex.getMessage.toLowerCase should include("login")
      }
    }

    "expose anthropic-workspace-id in extraHeaders when the config has a workspace_id" in {
      withTempProfile(
        configJson = configWithWorkspaceJson,
        credentialsJson = Some(
          credentialsJson(
            accessToken = "at-ws",
            expiresAt = now + 10000,
            refreshToken = "rt-ws"
          )
        )
      ) {
        (
          configDir,
          profile
        ) =>
          val provider = new TestProvider(configDir, profile, now, failingRefresh)

          provider.extraHeaders should contain(
            AnthropicOAuthConsts.workspaceIdHeaderName -> "ws-789"
          )
      }
    }
  }
}
