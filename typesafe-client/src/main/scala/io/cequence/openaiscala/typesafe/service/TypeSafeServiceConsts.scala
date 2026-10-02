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

  /**
   * Liquid AI serves its decision model d1 on the same System One API, under `/decisions` (its
   * `/v1/models` at the host root is the website - the `/decisions` prefix keeps both paths
   * right).
   */
  val liquidBaseUrl = "https://api.liquid.ai/decisions/"

  val liquidApiKeyEnvKey = "LIQUID_API_KEY"

  val liquidDefaultModel: String = TypeSafeModelId.liquid_d1_free
}
