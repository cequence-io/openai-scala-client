package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.typesafe.domain.TypeSafeModelId

/** Defaults and env keys shared with the official TypeSafe SDKs. */
object TypeSafeServiceConsts {

  val defaultBaseUrl = "https://api.typesafe.ai/"

  val defaultModel: String = TypeSafeModelId.jev_latest

  val apiKeyEnvKey = "TYPESAFE_API_KEY"

  /**
   * Overrides the API host, e.g. for a proxy; the `/v1/...` paths are appended by the client.
   */
  val baseUrlEnvKey = "TYPESAFE_BASE_URL"

  val defaultModelEnvKey = "TYPESAFE_DEFAULT_MODEL"

  /** Response header identifying the request on TypeSafe's side. */
  val requestIdHeader = "x-typesafe-request-id"
}
