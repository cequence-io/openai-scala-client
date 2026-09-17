package io.cequence.openaiscala.typesafe.domain

import io.cequence.openaiscala.domain.NonOpenAIModelId

/**
 * TypeSafe model names (the same constants as the TypeSafe AI section of
 * [[NonOpenAIModelId]]). Jev is the first System One model; `jev-latest` is the alias the
 * official SDKs default to and resolves to a dated build (the response's `model` names it).
 * `GET /v1/models` lists what a given account can use - the dated builds themselves are not
 * listed but are accepted by name (2026-09-16: `jev-latest` -> `jev-1.13.0`; the `jev-1.12`
 * the cookbooks pinned is already gone, so prefer the aliases).
 */
object TypeSafeModelId {
  val jev_latest: String = NonOpenAIModelId.jev_latest

  /** A preview of the next `jev-latest`: "should be better in most ways". */
  val jev_preview: String = NonOpenAIModelId.jev_preview

  /** The build `jev-latest` resolved to on 2026-09-16. */
  val jev_1_13_0: String = NonOpenAIModelId.jev_1_13_0
}
