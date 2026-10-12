package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.domain.decisions.CreateDecisionSettings
import io.cequence.openaiscala.service.ChatProviderSettings
import io.cequence.openaiscala.typesafe.domain.{
  DecisionImages,
  DecisionModelListing,
  DecisionProtocol,
  DecisionProvider,
  ModelMetadata,
  DecisionModelId
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
   * 128 questions." (live 2026-10-02). The paid `d1` ([[DecisionModelId.liquid_d1]]) also
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
    DecisionModelId.pplx_decider_v1_1_27b,
    decisionsPath = "v1/decisions",
    models = DecisionModelListing.Fixed(
      Seq(
        ModelMetadata(
          DecisionModelId.pplx_decider_v1_1_27b,
          "Perplexity's multimodal decision model, the update of 2026-10-06 (Decision Index 61.56).",
          "2026-10-06",
          Some(Seq("text", "image"))
        ),
        ModelMetadata(
          DecisionModelId.pplx_decider_v1_27b,
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
   * protocol: Liquid AI's open-weight d1 ([[DecisionModelId.liquid_d1_3b_gguf]]) once
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
    DecisionModelId.liquid_d1_3b_gguf,
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
   * Microsoft-Decision-1 ([[DecisionModelId.microsoft_decision_1]]) on a Microsoft Foundry
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
   * TypeSafe's protocol - Jev, Liquid's d1, Perplexity's decider, OpenAI's Luna,
   * Microsoft-Decision-1, Cloudflare's Clefs, Upstage's Solar Decides, Inception's Mercury
   * Decide, Together's Tev1, Kev 4B, NACE's Drex and Respan's Span-01 (19 models on
   * 2026-10-10; Span-01 judges with noul questions only, over a text state or a conversation
   * trace `{"input": [messages], "output": message}`). `listModels` asks for them with
   * `output_modalities=decisions` (OpenRouter's plain list leaves them out, and its
   * `input_modalities` understate: `liquid/d1` and Mercury Decide read images although listed
   * as text); the request id is the `x-generation-id`. Images go as `image_url` parts in the
   * state array ([[DecisionImages.InState]]; a top-level `images` is a 400 "Top-level `images`
   * is not supported") - live 2026-10-10 a blue square was blue at 0.9+ for Clef Omni, Clef
   * Flash, Perplexity's decider, Luna, d1 and Mercury Decide, read as text by Jev, Kev, Tev1,
   * Drex, the Solar Decides and Span-01, and Clef itself answered 0.99 once and 0.39 the next
   * time.
   */
  val openRouter: DecisionProvider = DecisionProvider(
    "https://openrouter.ai/api/",
    "OPENROUTER_API_KEY",
    DecisionModelId.openrouter_jev_latest,
    models = DecisionModelListing.OpenAIStyle(Seq("output_modalities" -> "decisions")),
    images = DecisionImages.InState,
    requestIdHeaders = Seq("x-generation-id"),
    name = Some("openrouter")
  )

  /**
   * Vercel AI Gateway (`AI_GATEWAY_API_KEY` - an AI Gateway key, `vck_...`, from the team's AI
   * Gateway > API keys; a Vercel account token is a 401): TypeSafe's protocol at
   * `https://ai-gateway.vercel.sh/typesafe/` - `POST v1/systemone`, the models at `GET
   * v1/models` in TypeSafe's shape (no input modalities): `typesafe-ai/jev` (the bare `jev`
   * too), `liquid/d1` (text + images), `microsoft/microsoft-decision-1`,
   * `openai/gpt-6-luna-decisions` and Convai Innovations' `convaiinnovations/laya` /
   * `laya-free`, billed through the gateway (`provider_metadata.gateway` in the response: the
   * routing, the cost, a `generationId`); the request id is the `x-vercel-id` header. Live
   * 2026-10-10 on a free-tier team (a card on file unlocks the free credits): Laya answers the
   * review in 0.5-1.4 s at 5 requests per minute per region (429 `rate_limit_exceeded` "Retry
   * after 46s"), every other model is a 403 `no_providers_available` "Free tier users do not
   * have access to this model" until credits are bought - so d1's images are not verified
   * here; the gateway takes an `image_url` part in the state array, as its AI SDK sends them
   * ([[DecisionImages.InState]]). It validates before routing (400 `invalid_request` in a flat
   * `{"message", "error_type"}` body): 2-10 score levels, at most 255 options, a string /
   * object / array state; an unknown model is a 404 `model_not_found`; an unknown top-level
   * field is ignored. A provider's own refusal comes back as a 422 / 413 `AI_APICallError` -
   * Laya (8k context) took 40 questions and answered 200 of them, or a 20k-token state, with
   * "That request is too large for this model" (a
   * [[TypeSafeScalaTokenCountExceededException]]), and a noul without instructions with "That
   * request was rejected". Its decision fallbacks (`providerOptions.gateway.models`) are the
   * gateway's own extension, not sent here.
   */
  val vercel: DecisionProvider = DecisionProvider(
    "https://ai-gateway.vercel.sh/typesafe/",
    "AI_GATEWAY_API_KEY",
    DecisionModelId.vercel_jev,
    images = DecisionImages.InState,
    requestIdHeaders = Seq("x-vercel-id"),
    name = Some("vercel")
  )

  /**
   * SubQ Snap (`subq-snap-preview`, Subquadratic, public preview since 2026-10-12;
   * `SUBQ_API_KEY`): a long-context decision model on TypeSafe's protocol at `POST
   * https://api.subq.ai/v1/systemone`, $0.05 per 1M input tokens, output free. Live
   * 2026-10-12: the largest input it takes is 295,957 tokens by its own count (`usage`), the
   * question included - one more is a 400 "Input exceeds the token limit." (a
   * [[TypeSafeScalaTokenCountExceededException]]), so ~77% of the launch post's 384K; ~230 ms
   * for one question on a kept-alive connection, ~320 ms for 10, ~410 ms for 40, ~0.7 s for a
   * 95k-token state. Up to 4 images as `{"mediaType", "base64"}` objects in a top-level
   * `images` array ([[DecisionImages.ImageObjects]]; an `image_url` part left in the state is
   * read as text). Errors are TypeSafe's `{"detail": {"error_type", "message"}}`: a 422 for an
   * unknown model (its chat model `subq-preview` included), a choice of one option, more than
   * 10 score levels, a noul without instructions or a question name over 100 characters; 402
   * is out of credits. No question cap (513 took), no request id header, an unknown top-level
   * field ignored. Its `/v1/models` would list the early-access chat model next to it for a
   * key that has one, so the list is fixed here.
   */
  val subq: DecisionProvider = DecisionProvider(
    "https://api.subq.ai/",
    "SUBQ_API_KEY",
    DecisionModelId.subq_snap_preview,
    models = DecisionModelListing.Fixed(
      Seq(
        ModelMetadata(
          DecisionModelId.subq_snap_preview,
          "SubQ Snap Preview - a long-context decision model (text + up to 4 images, at most 295,957 input tokens).",
          "2026-10-12",
          Some(Seq("text", "image"))
        )
      )
    ),
    images = DecisionImages.ImageObjects,
    requestIdHeaders = Nil,
    name = Some("subq")
  )
}
