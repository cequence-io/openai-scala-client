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
   * Images (Jev 1.13 and 1.14, "dreamer-ready" since 2026-10-14) go as TypeSafe's assets
   * ([[DecisionImages.Assets]]) - from the state and from questions alike, the request opting
   * in with [[TypeSafeImagesHeader]] by itself, as it does for a `BoxQuestion` (Jev 1.14
   * only). Live 2026-10-14: a red circle read red at 0.98-1.0 and as a circle at 1.0, a
   * reference picture in the instructions and options defined by example pictures worked,
   * ~0.2-0.45 s per call; boxes on 1.14 came within 0.02 of a shape's edges. The default
   * context is 33,024 input tokens per question and ~65.8k per request (Jev's own count) - see
   * [[typeSafeLongContext]] for 262k.
   */
  val typeSafe: DecisionProvider =
    DecisionProvider(
      defaultBaseUrl,
      apiKeyEnvKey,
      defaultModel,
      images = DecisionImages.Assets,
      name = Some("typesafe"),
      boxQuestions = true
    )

  /**
   * TypeSafe's opt-in to images and box questions ("danger zone", 2026-10-14) - the client
   * sends it itself with every request that carries either; without it such a request is a 400
   * "Images are experimental. Send the header TypeSafe-Danger-Zone-Images: true to use them."
   */
  val TypeSafeImagesHeader = "TypeSafe-Danger-Zone-Images"

  /**
   * TypeSafe's opt-in to long context ("danger zone", 2026-10-14): 262k tokens per request and
   * per question (live: 262,400 by Jev's own count) instead of 64k / 32k - at the same price,
   * but slower and less accurate past the default limits (TypeSafe's words). Sent with every
   * request by [[typeSafeLongContext]].
   */
  val TypeSafeLongContextHeader = "TypeSafe-Danger-Zone-Long-Context"

  /**
   * TypeSafe's Jev with long context switched on ([[TypeSafeLongContextHeader]] on every
   * request): up to 262,400 input tokens per question and per request instead of 33,024 /
   * ~65.8k (live 2026-10-14, both Jev 1.13 and 1.14: 120k tokens answered in 1.7 s, 250k in
   * 4.6 s; one more token than the limit is a 400 `max_tokens_exceeded`).
   */
  val typeSafeLongContext: DecisionProvider =
    typeSafe.copy(extraHeaders = Seq(TypeSafeLongContextHeader -> "true"))

  /**
   * Liquid AI's decision models (`LIQUID_API_KEY`): `d1` by default - its free tier `d1:free`,
   * the default until Liquid removed it on 2026-10-14, is gone - and `d1-ultra`
   * ([[DecisionModelId.liquid_d1_ultra]], 2026-10-15, the stronger). At most 128 questions
   * (422 "A request accepts at most 128 questions."). Both read images, sent in a top-level
   * `images` array ([[DecisionImages.ImagesField]]): at most 8, 10,000 32 x 32 patches in all,
   * no side over 100 times the other - each a quick 422. Every question is billed as its own
   * prompt (the state, its images and the question), which must stay under the model's input
   * limit - 65,536 tokens on `d1`, 32,768 on `d1-ultra`, or 250,000 bytes of text (a 422, a
   * `TypeSafeScalaTokenCountExceededException`) - and the state under 1 MB (live 2026-10-15).
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
   * Decide, Together's Tev1, Kev 4B, NACE's Drex, Respan's Span-01 and ScaleDown's Classify-1
   * (22 models on 2026-10-13; Span-01 judges with noul questions only, over a text state or a
   * conversation trace `{"input": [messages], "output": message}`; `scaledown/classify-1`
   * reads text only, so its requests go in [[DecisionProtocol.ScaleDown]] - `modelProtocols`,
   * live 2026-10-13: OpenRouter forwards its `document` when the text is not empty, and needs
   * instructions on every question). `listModels` asks for them with
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
    name = Some("openrouter"),
    modelProtocols = Seq("scaledown/" -> DecisionProtocol.ScaleDown)
  )

  /**
   * Vercel AI Gateway (`AI_GATEWAY_API_KEY` - an AI Gateway key, `vck_...`, from the team's AI
   * Gateway > API keys; a Vercel account token is a 401): TypeSafe's protocol at
   * `https://ai-gateway.vercel.sh/typesafe/` - `POST v1/systemone`, the models at `GET
   * v1/models` in TypeSafe's shape (no input modalities): `typesafe-ai/jev` (the bare `jev`
   * too), `liquid/d1` (text + images), `microsoft/microsoft-decision-1`,
   * `openai/gpt-6-luna-decisions`, Convai Innovations' `convaiinnovations/laya` / `laya-free`
   * and ScaleDown's `scaledown/scaledown-classify-1` (since 2026-10-13; text only - its
   * requests go in [[DecisionProtocol.ScaleDown]], `modelProtocols`), billed through the
   * gateway (`provider_metadata.gateway` in the response: the routing, the cost, a
   * `generationId`); the request id is the `x-vercel-id` header. Live 2026-10-10 on a
   * free-tier team (a card on file unlocks the free credits): Laya answers the review in
   * 0.5-1.4 s at 5 requests per minute per region (429 `rate_limit_exceeded` "Retry after
   * 46s"), every other model but ScaleDown's (open to it 2026-10-13) is a 403
   * `no_providers_available` "Free tier users do not have access to this model" until credits
   * are bought - so d1's images are not verified here; the gateway takes an `image_url` part
   * in the state array, as its AI SDK sends them ([[DecisionImages.InState]]). It validates
   * before routing (400 `invalid_request` in a flat `{"message", "error_type"}` body): 2-10
   * score levels, at most 255 options, a string / object / array state; an unknown model is a
   * 404 `model_not_found`; an unknown top-level field is ignored. A provider's own refusal
   * comes back as a 422 / 413 `AI_APICallError` - Laya (8k context) took 40 questions and
   * answered 200 of them, or a 20k-token state, with "That request is too large for this
   * model" (a [[TypeSafeScalaTokenCountExceededException]]), and a noul without instructions
   * with "That request was rejected". Its decision fallbacks
   * (`providerOptions.gateway.models`) are the gateway's own extension, not sent here.
   */
  val vercel: DecisionProvider = DecisionProvider(
    "https://ai-gateway.vercel.sh/typesafe/",
    "AI_GATEWAY_API_KEY",
    DecisionModelId.vercel_jev,
    images = DecisionImages.InState,
    requestIdHeaders = Seq("x-vercel-id"),
    name = Some("vercel"),
    modelProtocols = Seq("scaledown/" -> DecisionProtocol.ScaleDown)
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

  /**
   * ScaleDown's Constrained Decoding Models (`SCALEDOWN_API_KEY`, launched 2026-10-13):
   * `classify-1` (the default) and `classify` (the latest version - the same model today) at
   * `POST https://api.scaledown.xyz/v1/scaledown`, the key in an `x-api-key` header, in
   * ScaleDown's text-only System One ([[DecisionProtocol.ScaleDown]]: a state that is not text
   * goes as its pretty-printed text, an image part - one per request - as the state's
   * `document`, a noul's criteria appended to its instructions). $0.05 per 1M input tokens,
   * output free; each question is a classify call of its own, so the state is billed once per
   * question (~180-190 tokens per noul about a one-line review). `GET v1/models` lists
   * ScaleDown's compress, extract and summarize too, left out by their output modalities. Live
   * 2026-10-13 without credits (through OpenRouter and Vercel, which route to it, with
   * credits): the body is validated before the credits are checked - a malformed one, an
   * unknown model, an empty question map or a 1-level score is a 422, any valid one a 402
   * "Insufficient credits"; the request id is AWS API Gateway's `x-amzn-requestid`. A request
   * that takes over ~29 s is a 504 (the gateway's timeout): on its launch day, one in a few
   * took 15-30 s at random, and more than ~175 questions timed out (150 answered in 15 s, 175
   * in 7 s; 129 in 2-3 s).
   *
   * Its host takes TLS 1.3 only, which the Play WS engine offers since ws-client 1.1.2 (the
   * JDK's default client protocols). A JVM that pins the protocols - with
   * `play.shaded.ahc.org.asynchttpclient.enabledProtocols` or `jdk.tls.client.protocols` -
   * must keep `TLSv1.3` in the list, else the handshake fails ("Received close_notify during
   * handshake").
   */
  val scaleDown: DecisionProvider = DecisionProvider(
    "https://api.scaledown.xyz/",
    "SCALEDOWN_API_KEY",
    DecisionModelId.scaledown_classify_1,
    decisionsPath = "v1/scaledown",
    models = DecisionModelListing.OpenAIStyle(),
    images = DecisionImages.InState,
    requestIdHeaders = Seq("x-amzn-requestid"),
    name = Some("scaledown"),
    protocol = DecisionProtocol.ScaleDown,
    apiKeyHeader = Some("x-api-key")
  )
}
