package io.cequence.openaiscala.typesafe.domain

import io.cequence.openaiscala.domain.NonOpenAIModelId

/**
 * TypeSafe model names (the same constants as the TypeSafe AI section of
 * [[NonOpenAIModelId]]). Jev is the first System One model; `jev-latest` is the alias the
 * official SDKs default to and resolves to a dated build (the response's `model` names it).
 * `GET /v1/models` lists what a given account can use - the dated builds themselves are not
 * listed but are accepted by name (2026-09-17: `jev-latest` and `jev-preview` both ->
 * `jev-1.13.0`; the `jev-1.12` the cookbooks pinned is already gone). Pricing $0.042 per 1M
 * input tokens, output free; the state and the questions share an input limit of ~32k tokens
 * (~150k characters) - see the TypeSafe AI section of [[NonOpenAIModelId]].
 */
object TypeSafeModelId {
  val jev_latest: String = NonOpenAIModelId.jev_latest

  /** A preview of the next `jev-latest`: "should be better in most ways". */
  val jev_preview: String = NonOpenAIModelId.jev_preview

  /** The build `jev-latest` resolved to on 2026-09-16. */
  val jev_1_13_0: String = NonOpenAIModelId.jev_1_13_0

  /**
   * Liquid AI's decision model d1 (free tier) - the same System One API on Liquid's host (see
   * `TypeSafeServiceFactory.liquid`).
   */
  val liquid_d1_free: String = NonOpenAIModelId.liquid_d1_free

  /**
   * Perplexity's multimodal decision model - its Decisions API takes the same questions and
   * images in the state (see `TypeSafeServiceFactory.perplexity`).
   */
  val pplx_decider_v1_27b: String = NonOpenAIModelId.pplx_decider_v1_27b
}
