package io.cequence.openaiscala.typesafe.domain

import io.cequence.openaiscala.domain.NonOpenAIModelId

/**
 * The ids of every decision model the typesafe-client reaches: TypeSafe's Jev, Liquid AI's d1,
 * Perplexity's decider, Microsoft-Decision-1, and the decision lists of OpenRouter and Vercel
 * AI Gateway (the same constants as the decision-model section of [[NonOpenAIModelId]]). Its
 * name until 1.5.0, `TypeSafeModelId`, is a deprecated alias of this object. Jev is the first
 * System One model; `jev-latest` is the alias the official SDKs default to and resolves to a
 * dated build (the response's `model` names it). TypeSafe's `GET /v1/models` lists what a
 * given account can use; the dated builds themselves are not listed but are accepted by name
 * (2026-09-17: `jev-latest` and `jev-preview` both resolved to `jev-1.13.0`; the `jev-1.12`
 * the cookbooks pinned is already gone). Jev's pricing is $0.042 per 1M input tokens, output
 * free; the state and the questions share an input limit of ~32k tokens (~150k characters),
 * see the TypeSafe AI section of [[NonOpenAIModelId]].
 */
object DecisionModelId {
  val jev_latest: String = NonOpenAIModelId.jev_latest

  /**
   * A preview of the next `jev-latest`: "should be better in most ways" - Jev 1.14 since
   * 2026-10-14.
   */
  val jev_preview: String = NonOpenAIModelId.jev_preview

  /** The build `jev-latest` resolves to (since 2026-09-16; images since 2026-10-14). */
  val jev_1_13_0: String = NonOpenAIModelId.jev_1_13_0

  /**
   * Jev 1.14 (2026-10-14; `jev-preview`, `jev-latest` about a week later): small improvements
   * across the board, most in multilingual support - and the only build that answers a
   * `BoxQuestion`.
   */
  val jev_1_14_0: String = NonOpenAIModelId.jev_1_14_0

  /**
   * Liquid AI's decision model d1 (free tier) - the same System One API on Liquid's host (see
   * `TypeSafeServiceFactory.liquid`); removed by Liquid on 2026-10-14.
   */
  @deprecated(
    "Liquid removed d1:free on 2026-10-14 (a 404) - use liquid_d1 or liquid_d1_ultra",
    "1.6.0"
  )
  val liquid_d1_free: String = NonOpenAIModelId.liquid_d1_free

  /**
   * Liquid AI's d1, which also reads images (in a top-level `images` array) - the default of
   * `TypeSafeServiceFactory.liquid`.
   */
  val liquid_d1: String = NonOpenAIModelId.liquid_d1

  /**
   * Liquid AI's d1-ultra (2026-10-15), its strongest decision model: text and images like d1,
   * $0.042 / 1M input tokens, 32,768 tokens per question's prompt (d1: 65,536).
   */
  val liquid_d1_ultra: String = NonOpenAIModelId.liquid_d1_ultra

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

  /**
   * Perplexity's 27B decider v1.2 (2026-10-16, open weights; $0.02 per 1M input tokens) - the
   * Perplexity preset's default.
   */
  val pplx_decider_v1_2: String = NonOpenAIModelId.pplx_decider_v1_2

  /**
   * Perplexity's 4B Flash decider v1.2 (2026-10-16, open weights; $0.01 per 1M input tokens).
   */
  val pplx_decider_flash_v1_2: String = NonOpenAIModelId.pplx_decider_flash_v1_2

  /** The rolling aliases of Perplexity's latest decider and Flash decider. */
  val pplx_decider_latest: String = NonOpenAIModelId.pplx_decider_latest
  val pplx_decider_flash_latest: String = NonOpenAIModelId.pplx_decider_flash_latest

  /**
   * Microsoft-Decision-1 on Microsoft Foundry (public preview 2026-10-09) - see
   * `TypeSafeServiceFactory.microsoftFoundry`. The model's name (a deployment's `model`); a
   * request names your DEPLOYMENT of it, not the model - `listModels` lists them.
   */
  val microsoft_decision_1: String = NonOpenAIModelId.microsoft_decision_1

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
  val openrouter_clef_omni: String = NonOpenAIModelId.openrouter_clef_omni
  val openrouter_microsoft_decision_1: String =
    NonOpenAIModelId.openrouter_microsoft_decision_1
  val openrouter_drex_v1_5: String = NonOpenAIModelId.openrouter_drex_v1_5
  val openrouter_drex_v2_0: String = NonOpenAIModelId.openrouter_drex_v2_0
  val openrouter_pplx_decider_latest: String =
    NonOpenAIModelId.openrouter_pplx_decider_latest
  val openrouter_pplx_decider_v1_2: String = NonOpenAIModelId.openrouter_pplx_decider_v1_2
  val openrouter_pplx_decider_flash_v1_2: String =
    NonOpenAIModelId.openrouter_pplx_decider_flash_v1_2
  val openrouter_pplx_decider_flash_latest: String =
    NonOpenAIModelId.openrouter_pplx_decider_flash_latest
  // text only - its requests go in `DecisionProtocol.ScaleDown` (`openRouter.modelProtocols`)
  val openrouter_scaledown_classify_1: String =
    NonOpenAIModelId.openrouter_scaledown_classify_1
  val openrouter_jev_1_14: String = NonOpenAIModelId.openrouter_jev_1_14
  val openrouter_liquid_d1_ultra: String = NonOpenAIModelId.openrouter_liquid_d1_ultra
  val openrouter_span_02: String = NonOpenAIModelId.openrouter_span_02
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

  // decision models on Vercel AI Gateway (`DecisionProviderSettings.vercel`)
  val vercel_jev: String = NonOpenAIModelId.vercel_jev
  val vercel_liquid_d1: String = NonOpenAIModelId.vercel_liquid_d1
  val vercel_microsoft_decision_1: String = NonOpenAIModelId.vercel_microsoft_decision_1
  val vercel_gpt_6_luna_decisions: String = NonOpenAIModelId.vercel_gpt_6_luna_decisions
  val vercel_laya: String = NonOpenAIModelId.vercel_laya
  val vercel_laya_free: String = NonOpenAIModelId.vercel_laya_free
  val vercel_scaledown_classify_1: String = NonOpenAIModelId.vercel_scaledown_classify_1

  // SubQ Snap (`DecisionProviderSettings.subq`)
  val subq_snap_preview: String = NonOpenAIModelId.subq_snap_preview

  /**
   * ScaleDown's Classify, pinned to version 1 (2026-10-13) - see
   * `DecisionProviderSettings.scaleDown`.
   */
  val scaledown_classify_1: String = NonOpenAIModelId.scaledown_classify_1

  /** ScaleDown's Classify, always the latest version. */
  val scaledown_classify: String = NonOpenAIModelId.scaledown_classify
}
