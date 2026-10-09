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

  /** Liquid AI's paid d1, which also reads images (in a top-level `images` array). */
  val liquid_d1: String = NonOpenAIModelId.liquid_d1

  /**
   * Liquid AI's open-weight d1-3B (text and images) on a local llama.cpp server
   * (`DecisionProviderSettings.llamaCpp`) - its router-mode id.
   */
  val liquid_d1_3b_gguf: String = NonOpenAIModelId.liquid_d1_3b_gguf

  /** Liquid AI's open-weight d1-omni-600M (experimental) on a local llama.cpp server. */
  val liquid_d1_omni_600m_gguf: String = NonOpenAIModelId.liquid_d1_omni_600m_gguf

  /**
   * Perplexity's multimodal decision model, the 2026-10-06 update - its Decisions API takes
   * the same questions and images in the state (see `TypeSafeServiceFactory.perplexity`).
   */
  val pplx_decider_v1_1_27b: String = NonOpenAIModelId.pplx_decider_v1_1_27b

  /**
   * Perplexity's first decider (2026-10-01); the API answers like the update under this id.
   */
  val pplx_decider_v1_27b: String = NonOpenAIModelId.pplx_decider_v1_27b

  // decision models on OpenRouter (`DecisionProviderSettings.openRouter`)
  val openrouter_jev_latest: String = NonOpenAIModelId.openrouter_jev_latest
  val openrouter_jev_1_13: String = NonOpenAIModelId.openrouter_jev_1_13
  val openrouter_liquid_d1: String = NonOpenAIModelId.openrouter_liquid_d1
  val openrouter_pplx_decider_v1_1_27b: String =
    NonOpenAIModelId.openrouter_pplx_decider_v1_1_27b
  val openrouter_pplx_decider_v1_27b: String = NonOpenAIModelId.openrouter_pplx_decider_v1_27b
  val openrouter_gpt_6_luna_decisions: String =
    NonOpenAIModelId.openrouter_gpt_6_luna_decisions
  val openrouter_clef: String = NonOpenAIModelId.openrouter_clef
  val openrouter_clef_flash: String = NonOpenAIModelId.openrouter_clef_flash
  val openrouter_solar_decide_flash: String = NonOpenAIModelId.openrouter_solar_decide_flash
  val openrouter_mercury_decide: String = NonOpenAIModelId.openrouter_mercury_decide
  val openrouter_solar_decide: String = NonOpenAIModelId.openrouter_solar_decide
  val openrouter_mercury_decide_free: String = NonOpenAIModelId.openrouter_mercury_decide_free
  val openrouter_tev1_4b_experimental: String =
    NonOpenAIModelId.openrouter_tev1_4b_experimental
  val openrouter_kev_4b: String = NonOpenAIModelId.openrouter_kev_4b
  val openrouter_span_01: String = NonOpenAIModelId.openrouter_span_01
  val openrouter_span_01_lite: String = NonOpenAIModelId.openrouter_span_01_lite
  val openrouter_span_01_lite_free: String = NonOpenAIModelId.openrouter_span_01_lite_free
}
