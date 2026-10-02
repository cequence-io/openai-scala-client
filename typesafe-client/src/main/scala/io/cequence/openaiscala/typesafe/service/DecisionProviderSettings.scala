package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.service.ChatProviderSettings
import io.cequence.openaiscala.typesafe.domain.{
  DecisionModelListing,
  DecisionProvider,
  ModelMetadata,
  TypeSafeModelId
}

/**
 * The hosts of decision models this library knows - the counterpart of `ChatProviderSettings`
 * for the chat providers: `TypeSafeServiceFactory(DecisionProviderSettings.openRouter)`,
 * `TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.perplexity)`. Each is
 * live-verified; any other host that speaks the protocol takes a [[DecisionProvider]] of its
 * own.
 */
object DecisionProviderSettings {

  import TypeSafeServiceConsts._

  /**
   * TypeSafe's Jev (`TYPESAFE_API_KEY`) - Jev took 129 questions in one request, no cap known.
   */
  val typeSafe: DecisionProvider =
    DecisionProvider(defaultBaseUrl, apiKeyEnvKey, defaultModel, name = Some("typesafe"))

  /**
   * Liquid AI's d1 (`LIQUID_API_KEY`, `d1:free`) - 422 "A request accepts at most 128
   * questions." (live 2026-10-02).
   */
  val liquid: DecisionProvider = DecisionProvider(
    liquidBaseUrl,
    liquidApiKeyEnvKey,
    liquidDefaultModel,
    maxQuestions = Some(128),
    name = Some("liquid")
  )

  /**
   * Perplexity's Decisions API (`pplx-decider-v1-27b`; `PERPLEXITY_API_KEY`, else
   * `SONAR_API_KEY`): `POST /v1/decisions`, images in the state, at most 128 questions; its
   * `/v1/models` lists the Agent API models, so the one decision model is listed here.
   */
  val perplexity: DecisionProvider = DecisionProvider(
    "https://api.perplexity.ai/",
    "PERPLEXITY_API_KEY",
    TypeSafeModelId.pplx_decider_v1_27b,
    decisionsPath = "v1/decisions",
    models = DecisionModelListing.Fixed(
      Seq(
        ModelMetadata(
          TypeSafeModelId.pplx_decider_v1_27b,
          "Perplexity's multimodal decision model, the one model of its Decisions API.",
          "2026-10-01"
        )
      )
    ),
    maxQuestions = Some(128),
    images = true,
    name = Some("perplexity"),
    apiKeyEnvFallbacks = Seq(ChatProviderSettings.sonar.apiKeyEnvVariable)
  )

  /**
   * OpenRouter (`OPENROUTER_API_KEY`): many hosts' decision models behind one key, on
   * TypeSafe's protocol - Jev, Liquid's d1, Upstage's Solar Decide, Inception's Mercury
   * Decide, Together's Tev1, Kev 4B and Respan's Span-01 (live 2026-10-02; Span-01 judges with
   * noul questions only, over a text state or a conversation trace `{"input": [messages],
   * "output": message}`). `listModels` asks for them with `output_modalities=decisions`
   * (OpenRouter's plain list leaves them out); the request id is the `x-generation-id`.
   */
  val openRouter: DecisionProvider = DecisionProvider(
    "https://openrouter.ai/api/",
    "OPENROUTER_API_KEY",
    TypeSafeModelId.openrouter_jev_latest,
    models = DecisionModelListing.OpenAIStyle(Seq("output_modalities" -> "decisions")),
    requestIdHeaders = Seq("x-generation-id"),
    name = Some("openrouter")
  )
}
