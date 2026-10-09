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
   * The deployment name [[microsoftFoundry]] sends when none is given: Foundry proposes
   * `Microsoft-Decision-1`, which Azure's resource-name rule refuses (`ContainsReservedWord` -
   * "microsoft"), so a deployment needs a name of your own.
   */
  val FoundryDefaultDeployment = "decision-1"

  /** Where a Microsoft Foundry resource serves the System One protocol (live 2026-10-09). */
  val FoundryDecisionsPath = "providers/microsoft/v1/systemone"

  /**
   * Microsoft-Decision-1 ([[TypeSafeModelId.microsoft_decision_1]]) on a Microsoft Foundry
   * resource (public preview since 2026-10-09; built on Qwen3.5-9B; $0.042 / 1M input tokens
   * in the US and EU data zones, output free): TypeSafe's protocol at `POST
   * <endpoint>/providers/microsoft/v1/systemone` with a `Bearer` key (`FOUNDRY_API_KEY`; the
   * `api-key` header works too), live-verified 2026-10-09. `endpoint` is the resource's
   * endpoint (`https://<resource>.services.ai.azure.com`, Microsoft's `FOUNDRY_BASE_URL`; a
   * trailing `/providers/microsoft` is stripped). A request names a DEPLOYMENT of the model,
   * not the model (`"Microsoft-Decision-1"` as the model is a 404 `DeploymentNotFound`), so
   * `deployment` is the default model and `listModels` lists the resource's deployments of it
   * ([[DecisionModelListing.AzureDeployments]]); the response's `model` is
   * `microsoft-decision-1`. Live facts: at most 255 questions (a 422 for 256), 2-255 options
   * per choice, 2-10 score levels (a 400 for one of either), the state plus all questions
   * under 64,000 tokens (a 422, [[TypeSafeScalaTokenCountExceededException]]), the body under
   * 1 MiB (a 413); a noul needs no instructions there (the client still requires instructions
   * or criteria); no images (an `images` field is a 400, an image part in the state is read as
   * text); the request id is Azure's `apim-request-id` (an `x-request-id` comes too); the
   * deployment's rate limit rides in `x-ratelimit-limit-requests` (50 per minute by default).
   * 128 noul questions in ~0.7 s, a 30k-token state in ~0.9 s.
   */
  def microsoftFoundry(
    endpoint: String,
    deployment: String = FoundryDefaultDeployment
  ): DecisionProvider = DecisionProvider(
    foundryEndpoint(endpoint),
    "FOUNDRY_API_KEY",
    deployment,
    decisionsPath = FoundryDecisionsPath,
    models = DecisionModelListing.AzureDeployments(),
    maxQuestions = Some(255),
    requestIdHeaders = Seq("apim-request-id", "x-request-id"),
    name = Some("microsoft-foundry")
  )

  // the resource endpoint - without the `/providers/microsoft` the launch example's base URL
  // may carry (its "base URL excludes /v1/systemone")
  private def foundryEndpoint(endpoint: String): String = {
    val trimmed = endpoint.trim.stripSuffix("/")
    val providerSuffix = "/providers/microsoft"
    if (trimmed.toLowerCase.endsWith(providerSuffix)) trimmed.dropRight(providerSuffix.length)
    else trimmed
  }

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
