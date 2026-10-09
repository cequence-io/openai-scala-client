package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.domain.decisions.CreateDecisionSettings
import io.cequence.openaiscala.service.ChatProviderSettings
import io.cequence.openaiscala.typesafe.domain.{
  DecisionImages,
  DecisionModelListing,
  DecisionProtocol,
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
   * Liquid AI's d1 (`LIQUID_API_KEY`; `d1:free` by default) - 422 "A request accepts at most
   * 128 questions." (live 2026-10-02). The paid `d1` ([[TypeSafeModelId.liquid_d1]]) also
   * reads images, sent in a top-level `images` array ([[DecisionImages.ImagesField]]): at most
   * 8, 10,000 32 x 32 patches in all, no side over 100 times the other - each a quick 422, as
   * is an image for `d1:free` ("The model `d1:free` does not accept images.", live
   * 2026-10-07).
   */
  val liquid: DecisionProvider = DecisionProvider(
    liquidBaseUrl,
    liquidApiKeyEnvKey,
    liquidDefaultModel,
    maxQuestions = Some(128),
    images = DecisionImages.ImagesField,
    name = Some("liquid")
  )

  /**
   * Perplexity's Decisions API (`pplx-decider-v1.1-27b` by default, `pplx-decider-v1-27b` the
   * launch model; `PERPLEXITY_API_KEY`, else `SONAR_API_KEY`): `POST /v1/decisions`, images in
   * the state, at most 128 questions, an input under 262,144 tokens, $0.02 per 1M input tokens
   * (live 2026-10-08); its `/v1/models` lists the Agent API models, so the decision models are
   * listed here. An image of any size goes: the API scales it to about 2,100 tokens (a 4032 x
   * 3024 phone photo, or 8192 x 8192, answered in ~1 s on 2026-10-08) - the 2,048-tile cap
   * over which it used to time out is gone, so no `maxImageTiles` here.
   */
  val perplexity: DecisionProvider = DecisionProvider(
    "https://api.perplexity.ai/",
    "PERPLEXITY_API_KEY",
    TypeSafeModelId.pplx_decider_v1_1_27b,
    decisionsPath = "v1/decisions",
    models = DecisionModelListing.Fixed(
      Seq(
        ModelMetadata(
          TypeSafeModelId.pplx_decider_v1_1_27b,
          "Perplexity's multimodal decision model, the update of 2026-10-06 (Decision Index 61.56).",
          "2026-10-06",
          Some(Seq("text", "image"))
        ),
        ModelMetadata(
          TypeSafeModelId.pplx_decider_v1_27b,
          "Perplexity's first decision model (Decision Index 56.4).",
          "2026-10-01",
          Some(Seq("text", "image"))
        )
      )
    ),
    maxQuestions = Some(128),
    images = DecisionImages.InState,
    name = Some("perplexity"),
    apiKeyEnvFallbacks = Seq(ChatProviderSettings.sonar.apiKeyEnvVariable)
  )

  /**
   * OpenAI's Decisions API (`gpt-6-luna`, public beta since 2026-10-06; the client's
   * `OPENAI_SCALA_CLIENT_API_KEY`, else `OPENAI_API_KEY`): its own protocol
   * ([[DecisionProtocol.OpenAI]]) - the questions and answers are translated, so every routine
   * on a decision service runs on it. At most 200 questions, images as base64 data URLs
   * anywhere in the state (no size cap: OpenAI scales them); the request id is `x-request-id`
   * (live 2026-10-07). The same API natively: `OpenAIService.createDecision`.
   */
  val openAI: DecisionProvider = DecisionProvider(
    "https://api.openai.com/",
    "OPENAI_SCALA_CLIENT_API_KEY",
    CreateDecisionSettings.DefaultModel,
    decisionsPath = "v1/decisions",
    models = DecisionModelListing.Fixed(
      Seq(
        ModelMetadata(
          CreateDecisionSettings.DefaultModel,
          "OpenAI's decision model, the one model of its Decisions API (input $0.10 / 1M tokens).",
          "2026-10-06",
          Some(Seq("text", "image"))
        )
      )
    ),
    maxQuestions = Some(200),
    images = DecisionImages.InState,
    requestIdHeaders = Seq("x-request-id"),
    name = Some("openai"),
    apiKeyEnvFallbacks = Seq("OPENAI_API_KEY"),
    protocol = DecisionProtocol.OpenAI
  )

  /**
   * A local llama.cpp server (`llama-server`, `http://127.0.0.1:8080/` - copy the provider
   * with another `baseUrl` for another host or port) serving decision models on TypeSafe's
   * protocol: Liquid AI's open-weight d1 ([[TypeSafeModelId.liquid_d1_3b_gguf]]) once
   * llama.cpp loads it, and Julia-1, Laya, Lev, Kev, OpenJev, Nimble, Clef. A server of one
   * model ignores the request's model; a router (several) needs one of its ids (`listModels` -
   * its decision models only, by `architecture.output_modalities`). Images go in a top-level
   * `images` array and need a model with a projector (`--mmproj`) - else a 501, as for a model
   * that is not a decision model ([[TypeSafeScalaInvalidRequestException]]). The key
   * (`LLAMA_API_KEY`, as `llama-server --api-key` reads it) only when the server was started
   * with one. Live 2026-10-07 with llama.cpp b11476: Julia-1 ~160 ms and Laya ~1.5 s for three
   * questions on two CPU cores.
   */
  val llamaCpp: DecisionProvider = DecisionProvider(
    "http://127.0.0.1:8080/",
    "LLAMA_API_KEY",
    TypeSafeModelId.liquid_d1_3b_gguf,
    models = DecisionModelListing.OpenAIStyle(),
    images = DecisionImages.ImagesField,
    requestIdHeaders = Nil,
    name = Some("llama.cpp"),
    apiKeyRequired = false
  )

  /**
   * Microsoft-Decision-1 on Microsoft Foundry (public preview since 2026-10-09; built on
   * Qwen3.5-9B; $0.042 / 1M input tokens in the US and EU data zones, output free): TypeSafe's
   * protocol at `POST <your Foundry endpoint>/v1/systemone` with a `Bearer` key
   * (`FOUNDRY_API_KEY`), as Microsoft's launch example shows. The endpoint is per deployment -
   * pass your Foundry resource's base URL (the example's `FOUNDRY_BASE_URL`, without
   * `/v1/systemone`); the request id is Azure's `apim-request-id`. The model must be deployed
   * in your Foundry subscription first. NOT live-verified here (2026-10-09: no deployment to
   * test against; the route and header come from the launch post's example, which says to
   * confirm them in the Foundry quickstart), so `decisionsPath` or the model id may need
   * adjusting - `copy` the provider.
   */
  def microsoftFoundry(baseUrl: String): DecisionProvider = DecisionProvider(
    baseUrl,
    "FOUNDRY_API_KEY",
    TypeSafeModelId.microsoft_decision_1,
    models = DecisionModelListing.Fixed(
      Seq(
        ModelMetadata(
          TypeSafeModelId.microsoft_decision_1,
          "Microsoft's decision model for classification, routing and evaluation (public preview; " +
            "Qwen3.5-9B base, $0.042 / 1M input tokens).",
          "2026-10-09",
          Some(Seq("text"))
        )
      )
    ),
    requestIdHeaders = Seq("apim-request-id", "x-ms-request-id", "x-request-id"),
    name = Some("microsoft-foundry")
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
