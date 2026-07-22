package io.cequence.openaiscala.anthropic.service.auth

import play.api.libs.json._

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermissions}
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, Paths, StandardCopyOption}

/**
 * Flattened view of an `ant auth login` profile config file
 * (`<configDir>/configs/<profile>.json`).
 *
 * @param authType
 *   `authentication.type` - currently only `"user_oauth"` is supported end-to-end;
 *   `"oidc_federation"` is recognized but rejected by [[AnthropicOAuthProfile.loadConfig]].
 * @param clientId
 *   `authentication.client_id`
 * @param scope
 *   `authentication.scope`
 * @param credentialsPath
 *   `authentication.credentials_path` - overrides the default credentials file location when
 *   present (see [[AnthropicOAuthProfile.credentialsFilePath]]).
 * @param baseUrl
 *   top-level `base_url` - overrides [[AnthropicOAuthConsts.defaultBaseUrl]] when present.
 * @param organizationId
 *   top-level `organization_id`
 * @param workspaceId
 *   top-level `workspace_id` - surfaced via the `anthropic-workspace-id` header when present.
 */
case class AnthropicOAuthProfileConfig(
  authType: String,
  clientId: Option[String],
  scope: Option[String],
  credentialsPath: Option[String],
  baseUrl: Option[String],
  organizationId: Option[String],
  workspaceId: Option[String]
)

/**
 * Credentials-file shape (`<configDir>/credentials/<profile>.json` by default), written by
 * `ant auth login` and read/rotated by [[AnthropicOAuthProfileTokenProvider]].
 *
 * @param accessToken
 *   `access_token`
 * @param refreshToken
 *   `refresh_token`
 * @param expiresAt
 *   `expires_at` - Unix seconds (integer)
 * @param scope
 *   `scope`
 * @param organizationUuid
 *   `organization_uuid`
 * @param organizationName
 *   `organization_name`
 * @param accountEmail
 *   `account_email`
 */
case class AnthropicOAuthCredentials(
  accessToken: String,
  refreshToken: Option[String],
  expiresAt: Option[Long],
  scope: Option[String],
  organizationUuid: Option[String],
  organizationName: Option[String],
  accountEmail: Option[String]
)

/**
 * Resolution and (de)serialization helpers for `ant auth login` profiles - config-directory /
 * profile-name resolution (env vars, OS conventions, `active_config` file), config-file
 * loading/validation, and atomic credentials-file read/write. Mirrors the file layout and
 * precedence rules of the official Anthropic Python/TypeScript SDKs so that profiles created
 * by the `ant` CLI can be consumed as-is.
 */
object AnthropicOAuthProfile {

  /**
   * Resolves the Anthropic config directory using, in order: [[overrideDir]]; the
   * [[AnthropicOAuthConsts.envConfigDir]] env var; on Windows (`os.name` contains `"win"`)
   * `%APPDATA%\Anthropic`; else `$XDG_CONFIG_HOME/anthropic` when `XDG_CONFIG_HOME` is set;
   * else `~/.config/anthropic`.
   */
  def resolveConfigDir(overrideDir: Option[Path]): Path =
    overrideDir
      .orElse(nonEmptyEnv(AnthropicOAuthConsts.envConfigDir).map(Paths.get(_)))
      .getOrElse {
        val osName = sys.props.getOrElse("os.name", "").toLowerCase
        if (osName.contains("win")) {
          val appData = nonEmptyEnv("APPDATA").getOrElse(System.getProperty("user.home"))
          Paths.get(appData, "Anthropic")
        } else {
          nonEmptyEnv("XDG_CONFIG_HOME")
            .map(xdg => Paths.get(xdg, "anthropic"))
            .getOrElse(Paths.get(System.getProperty("user.home"), ".config", "anthropic"))
        }
      }

  /**
   * Resolves the active profile name using, in order: [[overrideName]]; the
   * [[AnthropicOAuthConsts.envProfile]] env var; the trimmed contents of
   * `<configDir>/active_config` when that file exists and is non-empty; else `"default"`.
   */
  def resolveProfileName(
    configDir: Path,
    overrideName: Option[String]
  ): String =
    overrideName
      .filter(_.nonEmpty)
      .orElse(nonEmptyEnv(AnthropicOAuthConsts.envProfile))
      .orElse(readActiveConfigProfile(configDir))
      .getOrElse("default")

  /** `<configDir>/configs/<profile>.json` */
  def configFilePath(
    configDir: Path,
    profile: String
  ): Path = configDir.resolve("configs").resolve(s"$profile.json")

  /**
   * `config.credentialsPath` (resolved relative to [[configDir]] when it is itself relative)
   * when present, else `<configDir>/credentials/<profile>.json`.
   */
  def credentialsFilePath(
    configDir: Path,
    profile: String,
    config: AnthropicOAuthProfileConfig
  ): Path =
    config.credentialsPath match {
      case Some(rawPath) =>
        val path = Paths.get(rawPath)
        if (path.isAbsolute) path else configDir.resolve(path)

      case None =>
        configDir.resolve("credentials").resolve(s"$profile.json")
    }

  /**
   * Loads and validates `<configDir>/configs/<profile>.json`. Throws
   * [[IllegalArgumentException]] if the file is missing or malformed, if `authentication.type`
   * is `"oidc_federation"` (not yet supported), or if it is any other unrecognized type.
   */
  def loadConfig(
    configDir: Path,
    profile: String
  ): AnthropicOAuthProfileConfig = {
    val path = configFilePath(configDir, profile)
    if (!Files.exists(path)) {
      throw new IllegalArgumentException(
        s"Anthropic OAuth config file not found at '$path'. Run 'ant auth login' first."
      )
    }

    val config = parseConfig(readJson(path), path.toString)

    config.authType match {
      case "user_oauth" => config
      case "oidc_federation" =>
        throw new IllegalArgumentException("OIDC federation profiles are not supported yet")
      case other =>
        throw new IllegalArgumentException(
          s"Unknown Anthropic OAuth authentication type '$other' in '$path'"
        )
    }
  }

  /**
   * Loads and validates a credentials file. Throws [[IllegalArgumentException]] if the file is
   * missing or malformed, if its `type` field is present and not
   * [[AnthropicOAuthConsts.credentialsFileType]], or if its `expires_at` field is present and
   * not an integer.
   */
  def loadCredentials(path: Path): AnthropicOAuthCredentials = {
    if (!Files.exists(path)) {
      throw new IllegalArgumentException(
        s"Anthropic OAuth credentials file not found at '$path'. Run 'ant auth login' first."
      )
    }

    parseCredentials(readJson(path), path.toString)
  }

  /**
   * Serializes [[credentials]] back to the credentials-file JSON shape and writes it
   * atomically: a temp file is created in the same directory as [[path]], chmod'd to
   * owner-only (`rw-------`) where the filesystem supports POSIX permissions, then moved into
   * place with [[java.nio.file.StandardCopyOption.ATOMIC_MOVE]] (falling back to a plain
   * replace-move if the filesystem doesn't support atomic renames).
   */
  def writeCredentialsAtomic(
    path: Path,
    credentials: AnthropicOAuthCredentials
  ): Unit = {
    val dir = Option(path.getParent).getOrElse(Paths.get("."))
    Files.createDirectories(dir)

    val tmp = Files.createTempFile(dir, s".${path.getFileName}", ".tmp")
    try {
      Files.write(
        tmp,
        Json.prettyPrint(credentialsJson(credentials)).getBytes(StandardCharsets.UTF_8)
      )

      try {
        val posixView = Files.getFileAttributeView(tmp, classOf[PosixFileAttributeView])
        if (posixView != null) {
          posixView.setPermissions(PosixFilePermissions.fromString("rw-------"))
        }
      } catch {
        case _: UnsupportedOperationException => // non-POSIX filesystem: best effort only
        case _: java.io.IOException           => // best effort only
      }

      try {
        Files.move(
          tmp,
          path,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING
        )
      } catch {
        case _: AtomicMoveNotSupportedException =>
          Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(tmp) // no-op once moved; cleans up on any failure path above
    }
  }

  private def nonEmptyEnv(name: String): Option[String] =
    sys.env.get(name).filter(_.nonEmpty)

  private def readActiveConfigProfile(configDir: Path): Option[String] = {
    val activeConfigFile = configDir.resolve("active_config")
    if (Files.exists(activeConfigFile)) {
      val content =
        new String(Files.readAllBytes(activeConfigFile), StandardCharsets.UTF_8).trim
      if (content.nonEmpty) Some(content) else None
    } else {
      None
    }
  }

  private def readJson(path: Path): JsValue =
    Json.parse(Files.readAllBytes(path))

  private def parseConfig(
    json: JsValue,
    source: String
  ): AnthropicOAuthProfileConfig = {
    val authentication = (json \ "authentication").toOption.getOrElse(
      throw new IllegalArgumentException(
        s"Missing 'authentication' section in Anthropic OAuth config file '$source'"
      )
    )

    val authType = (authentication \ "type")
      .asOpt[String]
      .getOrElse(
        throw new IllegalArgumentException(
          s"Missing 'authentication.type' in Anthropic OAuth config file '$source'"
        )
      )

    AnthropicOAuthProfileConfig(
      authType = authType,
      clientId = (authentication \ "client_id").asOpt[String],
      scope = (authentication \ "scope").asOpt[String],
      credentialsPath = (authentication \ "credentials_path").asOpt[String],
      baseUrl = (json \ "base_url").asOpt[String],
      organizationId = (json \ "organization_id").asOpt[String],
      workspaceId = (json \ "workspace_id").asOpt[String]
    )
  }

  private def parseCredentials(
    json: JsValue,
    source: String
  ): AnthropicOAuthCredentials = {
    (json \ "type").toOption.foreach {
      case JsString(AnthropicOAuthConsts.credentialsFileType) => // ok
      case JsString(other) =>
        throw new IllegalArgumentException(
          s"Unsupported Anthropic OAuth credentials type '$other' in '$source' " +
            s"(expected '${AnthropicOAuthConsts.credentialsFileType}')"
        )
      case other =>
        throw new IllegalArgumentException(
          s"Invalid 'type' field in Anthropic OAuth credentials file '$source': $other"
        )
    }

    val accessToken = (json \ "access_token")
      .asOpt[String]
      .getOrElse(
        throw new IllegalArgumentException(
          s"Missing 'access_token' in Anthropic OAuth credentials file '$source'"
        )
      )

    val expiresAt: Option[Long] = (json \ "expires_at").toOption.flatMap {
      case JsNull => None
      case JsNumber(n) if n.isWhole =>
        try Some(n.toLongExact)
        catch {
          case _: ArithmeticException =>
            throw new IllegalArgumentException(
              s"'expires_at' in Anthropic OAuth credentials file '$source' is out of range: $n"
            )
        }
      case other =>
        throw new IllegalArgumentException(
          s"'expires_at' in Anthropic OAuth credentials file '$source' must be an integer " +
            s"(Unix seconds), got: $other"
        )
    }

    def optStr(name: String): Option[String] = (json \ name).asOpt[String]

    AnthropicOAuthCredentials(
      accessToken = accessToken,
      refreshToken = optStr("refresh_token"),
      expiresAt = expiresAt,
      scope = optStr("scope"),
      organizationUuid = optStr("organization_uuid"),
      organizationName = optStr("organization_name"),
      accountEmail = optStr("account_email")
    )
  }

  private def credentialsJson(credentials: AnthropicOAuthCredentials): JsObject = {
    val base = Json.obj(
      "type" -> AnthropicOAuthConsts.credentialsFileType,
      "access_token" -> credentials.accessToken
    )

    val optional: Seq[(String, JsValue)] =
      credentials.refreshToken.map(v => "refresh_token" -> Json.toJson(v)).toSeq ++
        credentials.expiresAt.map(v => "expires_at" -> Json.toJson(v)).toSeq ++
        credentials.scope.map(v => "scope" -> Json.toJson(v)).toSeq ++
        credentials.organizationUuid.map(v => "organization_uuid" -> Json.toJson(v)).toSeq ++
        credentials.organizationName.map(v => "organization_name" -> Json.toJson(v)).toSeq ++
        credentials.accountEmail.map(v => "account_email" -> Json.toJson(v)).toSeq

    optional.foldLeft(base) { case (acc, (key, value)) => acc + (key -> value) }
  }
}
