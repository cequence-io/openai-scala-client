package io.cequence.openaiscala.typesafe.domain

/**
 * TypeSafe model names. Jev is the first System One model; `jev-latest` is the alias the
 * official SDKs default to. `GET /v1/models` lists what a given account can use.
 */
object TypeSafeModelId {
  val jev_latest = "jev-latest"

  // dated build the TypeSafe cookbooks pin (docs.typesafe.ai/cookbooks, 2026-09)
  val jev_1_12 = "jev-1.12"
}
