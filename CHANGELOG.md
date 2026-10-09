# Changelog

## 1.5.0 (unreleased)

The 2026-10-01 - 2026-10-09 work since v1.4.0 (2026-09-30): OpenAI's Decisions API (natively and as a
decision provider), decision models from Perplexity, Liquid AI (with images), OpenRouter and llama.cpp behind one
`DecisionProvider` abstraction, typed decisions and re-ranking on any decision model, a guardrails adapter, JSON schemas
derived from case classes by one shared IR on Scala 2 and 3, Claude Haiku 5.5 with the Anthropic access modes re-verified,
Mistral Large 4 with its chunked content and citations, Perplexity's decider v1.1, a 32 MiB stream frame cap (1.4.0:
1 MiB) and a server-sent-events decoder built for large events. Everything below is live-verified against the providers
unless stated otherwise.

Artifacts (Scala 2.12 / 2.13 / 3): `openai-scala-client`, `openai-scala-client-stream`, `openai-scala-anthropic-client`,
`openai-scala-google-gemini-client`, `openai-scala-google-vertexai-client`, `openai-scala-perplexity-sonar-client`,
`openai-scala-typesafe-client`, `openai-scala-claude-agent-client`, `openai-scala-count-tokens`, `openai-scala-guice` and
the envelope `openai-scala-all`.

### ⚠️ Upgrading from 1.4.x

**Recompile** - 1.5.0 is not binary compatible with 1.4.0 (a `javap` diff of the published jars: `OpenAIService` gained
the Decisions API, `ChunkMessageSpec` and the TypeSafe `ModelMetadata` gained a field, the TypeSafe `impl` constructors
changed); ws-client stays at 1.1.1.

**Source breaks** - typical code compiles unchanged:
- your own implementations of `OpenAIService` need `createDecision` (`OpenAIService extends OpenAIDecisionsService`;
  wrappers delegate it);
- positional pattern matches on `ChunkMessageSpec` (new `content_chunks`) and on the TypeSafe `ModelMetadata` (new
  `input_modalities`), both defaulted;
- TypeSafe: `SchemaQuestions.MaxRangeLevels` (32) is gone - `ScoreQuestion.MaxLevels` (10) is the real limit;
  `TypeSafeServiceImpl` and `OpenAITypeSafeChatCompletionService` are built from a `DecisionProvider` / with an
  `imageInput` flag now (use `TypeSafeServiceFactory`, which did not change for the existing calls);
- `TypeSafeServiceAdapters.retry` over a factory service returns a `RetryTypeSafeService with OpenAIDecisionsService`
  (the static type is still `TypeSafeService`).

**Behavior changes** (details below):
- streamed frames: one event / JSON line may now be up to **32 MiB** (1.4.0: 1 MiB - the 2-3 MiB image frames of the
  Responses image generation and of Gemini's image models failed the stream); configurable, see "Streams";
- a chat `content` that is a list of chunks (Mistral) is read instead of failing the response - 1.4.0 could not parse
  any Mistral reasoning answer;
- the JSON helper (`createChatCompletionWithJSON`) picks its mode as before - native `json_schema` for a model listed in
  `models-supporting-json-schema` (or in the call's `jsonSchemaModels`), the prompt-based `json_object` fallback for one
  that is not; what changed is the TypeSafe adapter, which now reads the schema back from that fallback's prompt, so an
  unlisted decision-model id (a new dated `jev-1.14.0`, an OpenRouter id) works instead of failing loudly as in 1.4.0;
- the TypeSafe adapter plans at most **10** score levels (`ScoreQuestion.MaxLevels`; 1.4.0 shipped an unverified 32 -
  the API refuses more than 10) and names its questions by escaped paths, so `{"a.b": ...}` and `{"a": {"b": ...}}`
  never share an answer (#128);
- `OpenAIErrorCodes` types two more token limits as `OpenAIScalaTokenCountExceededException` (batch splitters rely
  on it): the embeddings endpoint's per-request limit - a 400 `max_tokens_per_request` ("Requested 468160 tokens, max
  300000 tokens per request"; its per-input limit, "maximum input length is 8192 tokens", was typed already) - and
  the Decisions API's "Decision input exceeds the token limit." (`OpenAIErrorCodesSpec`);
- Anthropic: the retired `context-1m-2025-08-07` beta header is no longer sent (a Claude-subscription OAuth token
  refused every request carrying it); Bedrock structured outputs (`output_config.format`) are sent only to the
  inference profiles that accept them (see "Claude Haiku 5.5");
- the chat-completion batch emulation adapter runs its requests through `FutureHelpers.parallelize` (the next as soon
  as one finishes, results in order) instead of fixed-size groups.

### New models

- **Claude Haiku 5.5** (2026-10-07) - `NonOpenAIModelId.claude_haiku_5_5` / `bedrock_claude_haiku_5_5`: adaptive-only
  thinking (`enabled` and `between_tools` are 400s) with effort `low`..`max`, sampling params only at their defaults
  (the adapter drops them), no prefill, no fast mode, `json_schema` on the Claude API and on Bedrock; it keeps forced
  `tool_choice`. It thinks by default, so `reasoning_effort = none` sends `thinking.type = disabled` (effort `high`
  or below) - `noneThinkingByModel` maps `none` to Sonnet 5.5's `between_tools` and Haiku 5.5's `disabled`. On Bedrock
  it needs an inference profile (`global.` everywhere, `eu.` / `us.` regionally). `ClaudeHaiku55SmokeTest`.
- **Anthropic access modes** re-verified (`AnthropicAccessModesSmokeTest api|oauth|foundry|bedrock`): API key, a
  Claude-subscription OAuth token (`asOpenAIWithAuthToken`; outside Claude Code only Haiku 4.5 answers, every other
  model a bare 429 - Anthropic's gate), Microsoft Foundry (`customInstance` at
  `https://<resource>.services.ai.azure.com/anthropic/v1/`) and Bedrock (SigV4 and bearer token). Bedrock structured
  outputs re-probed per inference profile: the Claude 4.5 / 4.6 profiles and Haiku 5.5 accept `output_config.format`
  everywhere, Sonnet 5.5 on `global.` / `eu.`, Opus 5.5 on `eu.`; 4.7+ / 5.x profiles 400 - `models-supporting-json-schema`
  lists exactly the accepting ids.
- **Mistral Large 4** (`NonOpenAIModelId.mistral_large_4`, public preview 2026-10-06; 512k context, vision,
  `json_schema`, tools, prefix) and Mistral-hosted **GLM 5.3** (`zai-glm-5*`): `reasoning_effort` is a switch on
  Mistral's reasoning models (`mistralSwitchReasoning`: only `none` / `high`; `minimal` → `none`, the rest → `high`;
  GLM 5.3 `low` / `high` / `max`). A reasoning turn's `content` arrives as a LIST of `thinking` / `text` chunks, read into
  the text plus `reasoning_content` (→ `ChatChunk.Thinking`); Mistral's `reference` citation chunks ride raw in
  `ChunkMessageSpec.content_chunks` → `ChatChunk.Other("content.reference", chunk)` on the typed stream (Medium 3.5 and
  Magistral stream one as a delta of its own; the sync message reads drop them). `MistralLarge4SmokeTest`,
  `MistralCitationsSmokeTest`.
- **Perplexity's decider v1.1** - `pplx-decider-v1.1-27b` (2026-10-06; open weights, Decision Index 61.56 against
  56.4; the launch id answers alike - one model under two names), the Perplexity preset's default; input $0.02 / 1M
  (was $0.04). Images of any size: the API scales them to ~2,100 tokens, so the client's 2,048-tile refusal is gone -
  `DecisionImage(bytes)` / `fromDataUrl` check the format only, and a size cap applies only through a provider's
  `maxImageTiles` (no preset sets one).
- **Liquid AI d1 with images** - the paid `d1` (`TypeSafeModelId.liquid_d1`, text + image, $0.04 / 1M) next to `d1:free`
  (text only); `DecisionImages.ImagesField` lifts a state's image parts into Liquid's top-level `images` array (at most
  8 images, 10,000 patches in all). **Open d1** (d1-3B, d1-omni-600M; `liquid_d1_3b_gguf` / `liquid_d1_omni_600m_gguf`)
  and the local **llama.cpp** preset (`DecisionProviderSettings.llamaCpp`, `/v1/systemone`, router or single-model
  server) - llama.cpp b11476 could not yet load the d1 GGUFs; verified with Julia-1 / Laya.
- **OpenRouter's decision models** (`DecisionProviderSettings.openRouter`; listed with `output_modalities=decisions`):
  `~typesafe/jev-latest`, `liquid/d1`, `upstage/solar-decide`, `inception/mercury-decide:free`,
  `togethercomputer/tev1-4b-experimental`, `jaredpalmer/kev-4b`, `respan/span-01` (+ `-lite`, `-lite:free`; noul
  questions only) and, live 2026-10-09, the two `perplexity/pplx-decider-*` ids, `openai/gpt-6-luna-decisions`,
  `cloudflare/clef` / `clef-flash`, `upstage/solar-decide-flash` and `inception/mercury-decide` - 16 decision models,
  all answering the same review alike.
- **Groq's catalog** re-listed (`openai/gpt-oss-*`, `qwen/qwen3.6-27b` / `qwen3.8-27b`, `allam-2-7b`; the Llama 3.3 /
  4 and DeepSeek ids are gone).

### 🔥 OpenAI Decisions API (public beta)

`OpenAIDecisionsService.createDecision(input, questions, settings)` - part of the full `OpenAIService` - typed answers
about text and inline images from `gpt-6-luna` (launched 2026-10-06; $0.10 / 1M input tokens, no output charge):
`DecisionQuestion.Predicate` (a probability), `Choice` (text or boolean values) and `Score` (ordered levels, the
probability-weighted index), each answer a distribution with a `confidence`, plus `DecisionAnswer.Refusal`. Domain and
JSON in `domain/decisions/`; the request id (`x-request-id`) on `Decision.requestId`. Limits: 200 questions, 255
choices, 10 levels, 128 images as base64 data URLs, user messages only. `OpenAIDecisionsSmokeTest`.

**Either API on either host.** `TypeSafeServiceFactory.asOpenAIDecisions(provider | service)` serves OpenAI's interface
on a System One host (the questions translated; a factory service and the retry adapter over one serve it natively),
and `DecisionProviderSettings.openAI` serves System One's interface - `systemOne`, `decide[T]`, `rerank`, the guardrails
and the `asOpenAI` chat adapter - on OpenAI's Decisions API (`DecisionProtocol.OpenAI`, `impl/DecisionCodec`).
`CreateDecisionSettings.model` is optional (the host's default). Against Jev: the answers agree; OpenAI bills the input
once per question (~2.4x Jev at 3 questions, ~14x at 40) and slows with the count. `DecisionApiSwitchSmokeTest`.

### 🔥 Decision providers

`DecisionProvider` (`domain/`) describes a host of decision models like `ChatProviderSettings` describes a chat host:
base URL, key variable (+ fallbacks), default model, `decisionsPath` (`v1/systemone` or Perplexity's `v1/decisions`),
`models` (`TypeSafe` / `OpenAIStyle(query)` / `Fixed`), `maxQuestions`, `images` (`Unsupported` / `InState` /
`ImagesField`), `maxImageTiles`, `requestIdHeaders`, `protocol` (`SystemOne` / `OpenAI`), `apiKeyRequired`. Presets in
`DecisionProviderSettings`: `typeSafe`, `liquid`, `perplexity`, `openAI`, `llamaCpp`, `openRouter`;
`TypeSafeServiceFactory(provider)` / `withEngine(engine, provider)` / `asOpenAI(provider)` (image content per
`provider.readsImages`). Every host check runs before I/O: the question cap, the image format and a host's tile cap -
on `systemOne` and on `createDecision` alike.

### 🔥 Typed decisions and re-ranking

- `decide[T: JsonSchemaOf: Reads](state, model, noulThreshold)` (`DecisionServiceExtra`, an implicit class over any
  `TypeSafeService`): the schema of `T` planned into questions, asked, assembled and read back as `T` -
  `Decision[T](value, response)` with `noul` / `choice` / `score` by field path. `TypeSafeTypedDecision`.
- `rerank(query, passages, RerankSettings)` / `rerankBy(query, items)(text)`: one noul per passage (the passage in
  `<document>` tags, defused), batched by count and size, at most `parallelism` requests at once, identical texts asked
  once, a token-limit failure split in halves. `TypeSafeRerank` (Jev relevant 0.95 / partly 0.51 / injection 0.03).
- `ChoiceAnswer.probabilityOf` / `margin`, `ScoreAnswer.probabilityAtLeast`.

### 🔥 Guardrails adapter

`OpenAIServiceAdapters.guardrails(input, output, onViolation, outputReprompts, onVerdict)` and
`guardrailsWithStreaming(...)` guard `createChatCompletion` / `createChatToolCompletion` and both streams: the input
guardrails run before the call, the output guardrails check the reply (a stream's once it finished); a block fails with
`OpenAIScalaGuardrailException` (`Reject`) or answers with a message and `finish_reason = content_filter` (`Respond`);
`outputReprompts` asks again with the flagged reply. `ModelGuardrail` asks a stage's `GuardrailCheck`s in ONE
`json_schema` call to any chat service - an LLM or a decision model (`TypeSafeServiceFactory.asOpenAI`, a noul per check,
probabilities via `threshold`); only user text goes to the guard, tags inside it defused (`QuotedText`). Live: all 12
default checks right with gpt-5.4-mini (~0.9 s) and Jev (~250 ms). `GuardrailsSmokeTest`.

### JSON schema from a case class, on Scala 2 and 3

`JsonSchemaReflectionHelper.jsonSchemaFor[T]` builds one IR (`JsonSchemaShape`) by runtime reflection on Scala 2 and a
quotes macro on Scala 3, converted by one function - the versions cannot drift (`JsonSchemaDerivationSpec` on both).
The commonly used Scala and Java types are covered: the boxed and `java.math` numbers, `UUID` / `URI` / `URL` / `Locale`
/ `Currency` / `File` / `Path`, the `java.time` values, `Duration`s and `Period`s (Scala's `Duration` too), `Option` and
`java.util.Optional`, Scala and Java collections, a value class (`extends AnyVal`) as its underlying type (1.4.0 derived
it as an object `{value}`), and `Map[String, V]` / `java.util.Map` as an open object (`additionalProperties: true` - not
for OpenAI's strict mode, which closes every object). Enums keep their declaration order; `@JsonSchemaDescription`
(class, field, enum value) and `@JsonSchemaRange(min, max)` (`domain/JsonSchemaAnnotations`); an `Either`, a tuple, a
sealed hierarchy of case classes or a recursive type is refused (a compile error on Scala 3). `JsonSchemaOf[T]` (`service/`) is the derived-per-version instance for APIs that need a
schema per type (`decide[T]`). Value descriptions reach a decision model as per-option criteria.

### Streams

- **Frame cap** `StreamingConsts.maxFrameLength`: 32 MiB unless set via `openai-scala-client.streaming.maxFrameLength`
  (a HOCON size, e.g. `128 MiB`; env `OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH`), applied to every JSON stream and to
  the raw framings; a frame over it fails the stream with an exception naming the setting. Why 32 and not more: the cap
  bounds a stream's worst-case memory (~2-3x the frame), how much a delimiter-less broken stream buffers before failing
  and how long a dispatcher thread stalls parsing one; the JSON parser refuses a single string over 20M characters
  anyway. Live: the Responses image generation (2.2-2.7 MiB events) and Gemini 2.5 Flash Image (2.8 MiB), both over
  1.4.0's cap, read whole (`StreamedLargeFramesSmokeTest`).
- **Server-sent events decoder** (core `ServerSentEvents`, used by the Agents API session streams and the Perplexity
  Agent API): the pending event's rope is never walked per chunk - only its last 3 bytes plus the new chunk are
  searched for the boundary (a whole-buffer search was cubic in the chunks: 21 s for 1 MiB in 8 KiB chunks) - and an
  event is read on its bytes (one compaction, `Json.parse(bytes)`) instead of ~9 String copies.

### Changed

- `TypeSafeServiceAdapters.retry` over a factory service keeps the service's own Decisions path
  (`asOpenAIDecisions(retry(service))` no longer translates); `imageInput` is ignored, with a warning, for a service
  that serves the interface itself.
- `Decision.requestId` / `SystemOneResponse.requestId` read the host's id headers through one core helper,
  `ResponseHeaders.first` (case-insensitive, by priority, blank values skipped).
- `UsageInfo.sumOption`; `FutureHelpers.parallelize` (core).
- `OpenAIChatCompletionExtra.jsonSchemaFromPrompt` keeps an empty or blank user message the schema was appended to.
- TypeSafe: `TypeSafeChatMapping.toState(messages, images)` is public; errors carry `httpCode` / `errorType` /
  `requestId` via the shared `ProviderErrorDetails`; Liquid's and Perplexity's OpenAI-style error bodies are classified
  by status with `error.type` / `error.code`; `ModelMetadata.input_modalities`.
- Perplexity's Decisions OpenAPI spec vendored (`perplexity-decisions-openapi.json`) and pinned; Perplexity Agent /
  TypeSafe wire specs extended.

### Deprecated

- `NonOpenAIModelId.groq_compound` / `groq_compound_mini` - gone from Groq's catalog (404 since 2026-10-09).
- `ReflectionUtil` (Scala 2 and 3) - unused since the IR rewrite, to be removed.
- `MessageConversions`' reasoning helpers now point at `ChunkMessageSpec.reasoningText` / `ChatChunk.Thinking` (a sync
  response carries no reasoning).

### Changed defaults

- Stream frame cap 1 MiB → 32 MiB (configurable).
- `DecisionProviderSettings.perplexity` defaults to `pplx-decider-v1.1-27b` and lists both deciders; no image size cap.
- `ScoreQuestion.MaxLevels` 32 → 10 (the APIs' real limit).

## 1.4.0 (2026-09-30)

39 commits since v1.3.0 (2026-09-18), 235 files, +35k lines: GPT-6.1 Sol with the Fast / Ultrafast tiers and the
Responses reasoning mode, the OpenAI Agents API and Responses multi-agent execution (both beta), human approval
mid-stream, Perplexity's Agent API, classified streaming errors (ws-client 1.1.1), Claude Opus 5.5 / Sonnet 5.5, Liquid
AI's decision model d1, model conversions re-measured against the live APIs, and the retirement sweep of dead models and
endpoints. Everything below is live-verified against the providers unless stated otherwise. 1.3.1 was never released -
its changes are part of 1.4.0.

Artifacts (Scala 2.12 / 2.13 / 3): `openai-scala-client`, `openai-scala-client-stream`, `openai-scala-anthropic-client`,
`openai-scala-google-gemini-client`, `openai-scala-google-vertexai-client`, `openai-scala-perplexity-sonar-client`,
`openai-scala-typesafe-client`, `openai-scala-claude-agent-client`, `openai-scala-count-tokens`, `openai-scala-guice` and
the envelope `openai-scala-all`.

### ⚠️ Upgrading from 1.3.x

**Recompile** - 1.4.0 is not binary compatible with 1.3.0 (case classes gained fields, service traits gained methods);
nothing public was removed. ws-client moves from 1.0.0 to 1.1.1.

**Source breaks** - typical code (building settings, calling services, reading results) compiles unchanged:
- positional pattern matches on `ReasoningConfig`, `CreateModelResponseSettings`, `Message.OutputContent` and
  `AssembledChatCompletion` (new fields, all defaulted);
- `UsageInfo.tupled` / `curried` are gone (Scala 2) - use `(UsageInfo.apply _).tupled`;
- your own implementations of `OpenAIService`, `OpenAIStreamedServiceExtra` or `SonarService` need the new methods;
- exhaustive matches meet new cases (`ChatChunk.ToolApprovalRequest`, `FinishReason.approval_required`, new
  `ServiceTier`, `ThinkingType` and image size / quality values).

**Behavior changes** (details below):
- streamed HTTP errors and in-band error frames fail classified (so the retry adapters retry them); Vertex AI errors are
  classified by gRPC status; native Sonar calls fail with `PerplexityScalaClientException`;
- per-model settings re-measured - the OpenAI conversions (now also dropping `stop` / `logit_bias` where rejected),
  Gemini thinking, the JSON-schema model list; OpenAI tool calls that keep reasoning go through the Responses API; no
  global `OpenAI-Beta: assistants=v2` header;
- refusals instead of silent drops: `setResponsesTools` outside the Responses API, `MCPServerTool(requireApproval)` on
  Anthropic and Gemini;
- new default models for image generation, web search, speech and transcription (the old ones are shut down);
- the typed stream finishes as `tool_calls` after tool calls; Responses reads are more lenient.

### New models

- **GPT-6.1 Sol** (DevDay 2026-09-29) - `ModelId.gpt_6_1_sol` plus Bedrock `openai.gpt-6.1-sol` (the `global.` and,
  since 2026-09-30, `us.` inference profiles), both with `json_schema` structured output. Reasoning is always on, so it gets the GPT-6
  **Astra** rules, not GPT-6 Sol's (live-verified 2026-09-29): `reasoning_effort` `none` / `minimal` → `low` (both APIs
  reject them), `max` → `xhigh` on chat completions (kept on the Responses API), sampling params stripped, `max_tokens` →
  `max_completion_tokens`, and function tools always go through the Responses API (chat completions rejects them with
  every effort) - a chat-only service fails fast. GPT-6 minors are now dispatched on the parsed version
  (`ChatCompletionSettingsConversions.gpt6Minor`; any newer GPT-6 minor gets the 6.1 rules). See `GPT61SolSmokeTest`.
- **GPT-6 Sol / Luna** - `ModelId.gpt_6_sol` / `gpt_6_luna`, plus Bedrock `openai.gpt-6-sol` / `openai.gpt-6-luna` /
  `openai.gpt-6-astra` (`us.` / `global.` inference profiles), all with `json_schema` structured output. Unlike GPT-6
  Astra they keep the GPT-5.6 rules: sampling params stripped, `max_tokens` → `max_completion_tokens`, `reasoning_effort`
  `max` → `xhigh` and `minimal` → `low` on chat completions while `none` is kept. There is no `gpt-6-terra` yet.
- **Tools keep reasoning on GPT-5.6 / GPT-6** - the chat completions API takes function tools from these models only with
  `reasoning_effort = none`, so the full OpenAI service now routes their `createChatToolCompletion` and typed
  `createChatToolCompletionStreamed` calls through the Responses API unless `none` is requested (GPT-6 Astra always). The
  Responses adapter keeps `max`, maps `minimal` → `low` (and `none` → `low` on Astra), and drops `temperature` / `top_p` /
  `top_logprobs`, which the Responses API rejects for these models. A chat-only service still forces `none`.
- **Claude Opus 5.5** - `NonOpenAIModelId.claude_opus_5_5` / `bedrock_claude_opus_5_5`: adaptive thinking with
  `output_config.effort` up to `max`, no sampling params, a 128k output cap, `json_schema` structured output on the Claude
  API (on Bedrock it rejects `output_config.format` like Opus 5, so the JSON helper uses prompt mode there), and no forced
  `tool_choice` - a forced tool is downgraded to `auto` plus a system instruction, as for Fable 5.1.
- **Claude Sonnet 5.5** - `NonOpenAIModelId.claude_sonnet_5_5` / `bedrock_claude_sonnet_5_5` (Bedrock serves it through
  the `global.` inference profile only): adaptive thinking with `output_config.effort` up to `max`, no sampling params, a
  128k output cap, `json_schema` structured output on the Claude API (prompt-mode JSON on Bedrock, which rejects
  `output_config.format`), and no forced `tool_choice` (downgraded to `auto` plus a system instruction). It rejects
  `thinking.type = disabled`; its lowest setting is the new `between_tools` (no up-front thinking), which
  `reasoning_effort = none` now maps to - `ThinkingType` gains `between_tools` and `disabled` (with
  `ThinkingSettings.betweenTools` / `disabled`), and the typed stream no longer adds a `display` to them.
- **OpenAI** - `ModelId.gpt_live_1` (the full-duplex voice model; Realtime API only). `gpt-3.5-turbo-instruct`,
  `gpt-3.5-turbo-1106`, `babbage-002` and `davinci-002` were shut down on 2026-09-28 (their deprecation notes say so now).
- **Other providers** (listed 2026-09-29) - Gemini `gemini-3.8-flash-tts` / `-flash-lite-tts`, `gemini-3.8-live` /
  `-live-extended-thinking` (Live API only) and `antigravity-preview-09-2026` / `-latest`; Mistral-hosted GLM 5.3
  (`zai-glm-5-3`) and `labs-leanstral-1-5-1`; Together AI `deepseek-ai/DeepSeek-V4.1-Flash`; Fireworks `ember-1`.
- **Meta Muse Glimmer 30B** - `NonOpenAIModelId.meta_models_muse_glimmer_30b` (Together AI): Meta's open-weights agentic
  model, a reasoning model (its reasoning arrives as `Thinking` chunks and counts toward `max_tokens`) with tools, image
  input and strict `json_schema` (added to `models-supporting-json-schema`). Fireworks lists it as `muse_glimmer_30b` for
  on-demand deployments only (not serverless). See `togetherai/MuseGlimmer30BSmokeTest`.

See `GPT61SolSmokeTest`, `GPT6SolLunaOpus55SmokeTest` and `anthropic/ClaudeSonnet55SmokeTest` for live walkthroughs.

### OpenAI DevDay 2026: service tiers, reasoning mode and multi-agent execution

- **`ServiceTier.priority` / `fast` / `ultrafast`** - `priority` and `fast` are the same Fast mode (a GPT-6 response
  reports `fast`, older models `priority`); `ultrafast` is the Ultrafast tier (about 6x the price and up to 6x the speed,
  US / global processing only), which only the Responses API serves - GPT-6 Astra as of 2026-09-29 (GPT-5.6 Sol in
  preview, GPT-6.1 Sol announced), while the chat completions API answers every model with 400 "Invalid service_tier
  argument". The full `OpenAIService` therefore routes a chat
  completion asking for it through the Responses API (sync, tools, the OpenAI-shaped and the typed stream); a chat-only
  service leaves it to the API.
- **Reasoning mode and context on the Responses API** - `ReasoningConfig.mode` (`ReasoningMode.standard` / `pro`: more
  model work for difficult tasks; GPT-6 models - GPT-5.5 and Bedrock reject it) and `ReasoningConfig.context`
  (`ReasoningContext.auto` / `current_turn` / `all_turns`). A Response echoing a value this client doesn't know yet
  (also for `effort`) reads it as absent instead of failing. From the chat interface,
  `settings.setResponsesReasoningMode(ReasoningMode.pro)` routes the call through the Responses API on the full service;
  everything that would send it to chat completions refuses it (`ResponsesChatCompletionSettingsOps.unsupportedResponsesSettings`,
  which also covers `setResponsesTools`).
- **Multi-agent execution (beta, GPT-6.1 Sol)** - `CreateModelResponseSettings.multiAgent = Some(MultiAgentConfig(...))`
  lets the model spawn, message and wait for subagents on the server; the client adds the required
  `OpenAI-Beta: responses_multi_agent=v1` header when it is enabled (sync and streamed; `enabled = false` needs none). The run's items - `MultiAgentCall`,
  `MultiAgentCallOutput`, `AgentMessage` - and every message carry the producing agent (`AgentTag`, `/root` or
  `/root/<task>`), valid as input for a stateless follow-up too; `Response.outputText` / `outputMessageContents` are the
  root agent's answer only (the subagents' messages, which the API interleaves with it, are in `subagentMessages`). From
  the chat interface `settings.setResponsesMultiAgent()` routes the call through the Responses API (refused on chat-only
  services); the typed stream shows the delegation as server-side `multi_agent.<action>` tool calls / results, the
  subagents' messages as `Other("subagent.message", ...)`, and never asks for reasoning summaries, which the API rejects
  with multi-agent (live-verified 2026-09-30). See `responsesapi/CreateModelResponseMultiAgent`.
- `ChatCompletionSettingsConversions.chatRequiresResponsesAPI(settings)` - the one predicate for settings only the
  Responses API can serve (approval decisions, Responses-native tools, a reasoning mode, multi-agent execution, the
  Ultrafast tier).
- **No always-on `OpenAI-Beta: assistants=v2` header any more** - the Assistants API it opted into is shut down (vector
  stores answer without it), and OpenAI reads only the FIRST `OpenAI-Beta` header of a request (live-verified
  2026-09-30), so the global header masked per-call betas such as `responses_multi_agent=v1` (a 400).

### 🔥 OpenAI Agents API (beta)

- **`OpenAIAgentsService`**, part of the full `OpenAIService` (every call sends `OpenAI-Beta: agents=v1`): durable cloud
  agents on a managed Codex harness - reusable agents (`createAgent` / `listAgents` / `getAgent` / `updateAgent` /
  `deleteAgent`), sessions (`createAgentSession` / `getAgentSession` / `listAgentSessions` / `updateAgentSession` /
  `deleteAgentSession`), input events (`sendAgentSessionEvents`: messages, client function results, cancellation,
  computer-use approvals; `Idempotency-Key`), a session's items / turns / subagents (incl. a subagent's items and turns),
  artifacts (incl. their content as a byte stream) and environments (templates, files - as JSON).
- **Streaming** on the streamed service: `createAgentSessionStreamed(settings, input)` - the create call's own stream,
  which the server closes once the session idles - and `streamAgentSessionEvents(sessionId)`, the subscription the server
  holds open (heartbeats every 15 s; end it with `.via(AgentSessionEvents.untilSettled())`). Typed `AgentSessionEvent`s
  (`SessionUpdated`, `TurnUpdated`, `ItemAdded` / `ItemDone`, text / reasoning-summary deltas, command output, `Error`)
  and `AgentSessionItem`s (messages with their `commentary` / `final_answer` phase, reasoning, client function calls and
  outputs, MCP calls, commands, web searches); everything else - content parts, environment and subagent events, subagent
  call items, computer use - arrives raw (`Other`), never failing a stream. `AgentSessionEvents.finalAnswer` collects the
  answer.
- Domain: `AgentConfig` (model, instructions, reasoning, text, service tier incl. `ultrafast`, tools, multi-agent),
  `AgentTool` (`Function`, `Mcp` over `Http` / `Stdio`, `WebSearch`, `ComputerUse`, `ToolSearch`,
  `ProgrammaticToolCalling`, `Raw` for anything else), `AgentEnvironment` (`NoEnvironment`, `OpenAIHosted`,
  `SelfHosted`), `AgentSessionInput`, `AgentRequiredAction` (a pending function call's arguments are a JSON object).
- Live facts (2026-09-30), also in the scaladoc: a client function call pauses the session (`requires_action`) until its
  result is posted - the created session's stream stays open, so post the result and keep consuming; a subscription
  opened on an idle session delivers the next turn (subscribe first, then send), one opened mid-turn replays the turn's
  items without text deltas; a session paused mid-turn cannot be deleted before it is cancelled (409). See
  `agents/OpenAIAgentsApiSmokeTest` (six live sections incl. a hosted sandbox command and a multi-agent session).
- **`service.agentsAsChatCompletion(...)`** (`OpenAIAgentsChatCompletionService`): the OpenAI chat-completion view of the
  Agents API - each call runs one session turn with an inline agent (`settings.model`, system messages as instructions,
  reasoning effort, verbosity, service tier, json_schema output as the agent's text format - closed objects, the API
  validates strictly - the call's function / MCP tools plus the adapter's `agentTools`, optional multi-agent) or a
  reusable `agentId`, in the adapter's `environment` (none by default, or an OpenAI-hosted sandbox). The typed stream
  reports the `final_answer` messages as `Text`, the agent's `commentary` and reasoning summaries as `Thinking`, commands
  (plus `CodeExecution*`), MCP calls, web searches and subagent calls as server-side tool calls / results. Client function
  tools work as a chat tool loop: a call pauses the session (`Finish(tool_calls)`, the session kept), and the next call
  carrying the `ToolMessage`s resumes it - subscribe, post the results once the subscription is live, skip the replayed
  items. A turn's session is settled at the turn's end, before its final chunks go out (a pause registered, a finished
  session's DELETE started and tracked), so neither a resume nor `close()` - which waits for cleanups still in flight -
  can overtake it, even after a consumer that stopped reading at the `Finish`; a turn that failed or was abandoned
  mid-way is cancelled, then deleted; a session stays paused - and resumable - until a resume has posted its results;
  `close()` cancels and deletes paused ones. History is folded into one labeled user message (the API takes user
  messages only; a deprecated `MessageSpec` by its role); a json_schema is always closed, the map form too; `error`
  session events are classified by their code (transient ones retryable); file content parts are refused. Live:
  `OpenAIAgentsApiSmokeTest`, five adapter sections.
- `io.cequence.openaiscala.service.ServerSentEvents` - the SSE decoder over a raw byte stream (comment heartbeats, CRLF
  framing, multi-line data, a non-SSE error body surfaced), moved to core from the Perplexity module, which now uses it.

### 🔥 Human approval mid-stream (typed stream)

- A run the provider pauses until a tool call is approved now surfaces on the typed stream as one
  `ChatChunk.ToolApprovalRequest` per pending call followed by `Finish(FinishReason.approval_required)`
  (`AssembledChatCompletion.toolApprovalRequests` / `awaitingApproval` / `approveAll` / `denyAll`), and is resumed by a
  second call carrying the decisions - `request.approve` / `request.deny(reason)` via
  `ToolApprovalSettingsOps.setToolApprovalDecisions`:
  - **OpenAI** (Responses API): `MCPServerTool(requireApproval = true)` (or a raw `MCPTool`) pauses the run. Such a run
    is stored by default (only when a tool may actually ask - Zero-Data-Retention organisations pass `store = false`)
    and the resume continues it by `previous_response_id`, sending the same tools and only the answers plus the
    outputs of the paused response's own client function calls (looked up from the stored response; outputs it already
    had are not sent again) - so reasoning and executed MCP calls carry over and a run may pause several times. With
    an explicit `store = false` the adapter replays the answered requests instead (one pause deep). The sync
    `createChatToolCompletion` and `createChatCompletion` report `finish_reason = "approval_required"` with
    `response.toolApprovalRequests`.
  - **Anthropic Managed Agents**: a native typed stream for `managedAgentAsOpenAI` - tools with an `always_ask` / `auto`
    permission policy pause the session, which is kept until a resume posts the `user.tool_confirmation`s to it (and
    kept when a resume can be retried: its confirmations were not applied, or its next pause could not be looked up);
    the typed stream also reports the agent's tool calls and results, usage and a proper finish reason, deletes a
    finished (or failed) session before completing, rides out `session.error`s the platform retries, and streams on
    through the idle the session reports between the confirmations of one resume. An `exhausted` session error (the
    turn is dead) now fails the call classified - on the sync path too - as does an event stream that closes before
    the turn ended. `PermissionPolicy.auto` added; a confirmation's `deny_message` is only sent with a denial.
  - Everything that cannot pause or resume now refuses instead of silently misbehaving: a call carrying decisions is
    refused by every other adapter / entry point (rather than starting a fresh run), and
    `MCPServerTool(requireApproval = true)` is refused by Anthropic's MCP connector, Gemini and Perplexity's
    `agentAsOpenAI` (rather than running the calls unapproved - it also refuses a raw `MCPTool` whose explicit
    `requireApproval` may ask). Responses-native tools (`setResponsesTools`) now route to the Responses API on the
    full OpenAI service's `createChatCompletion` / `createChatToolCompletion(Streamed)`; every other entry point (and a
    chat-only service) refuses them instead of dropping them, and the adapter-only `extra_params` keys are no longer
    sent to the chat completions API.
  - **Or let a callback answer**: `createChatToolCompletionStreamedWithApprovals(messages, tools, ...) { request => ... }`
    (on every streamed service) asks the callback (`ToolApprovalRequest => Future[ToolApprovalDecision]`) about each
    pending call, resumes the run with the answers and joins all rounds into one stream that ends with the final
    answer - one `Start`, no intermediate `Finish(approval_required)`, tool-call ordinals continuing, usage summed. It
    stops, paused as before, after `maxApprovalRounds` resumes (default 10) or when a paused round also asks for client
    function calls. `UsageInfo.sum` adds up usage.
  - `ChatChunk` gains a case (`ToolApprovalRequest`) and `FinishReason` a value (`approval_required`): exhaustive
    matches over them need a new branch.
  - See `CreateChatToolCompletionStreamedWithApproval` (resuming by hand) and
    `CreateChatToolCompletionStreamedWithApprovalCallback` (the callback), both live-verified on both providers - the
    latter with a two-pause OpenAI run and two parallel confirmations on Managed Agents.

### TypeSafe (Jev): confidence fields

- The OpenAI adapter (`TypeSafeServiceFactory.asOpenAI`) fills **confidence fields** instead of asking for them: a
  `number` property named `<field>_confidence` or `<field>Confidence` next to a sibling `<field>` (at any depth; both
  spellings may be declared) is dropped from the questions and filled with System One's confidence in that field's
  answer, right after it, rounded to 4 decimals - a boolean's probability of the emitted answer, a choice's / score's
  `confidence`, the weakest option of a multi-select, the minimum over everything under an object. A confidence field
  without a sibling is planned (and refused) as usual; a non-number one is refused. See `SchemaQuestions` and
  `TypeSafeOpenAIAdapterScenarios` (7b).

### 🔥 Liquid AI d1 - a second System One provider

Liquid AI's first decision model **d1** (launched 2026-09-30) is served on the same System One API as TypeSafe's Jev, so
the typesafe-client reaches it unchanged - only the host, the key and the model differ:
- **Works - decisions**: `TypeSafeServiceFactory.liquid()` / `liquidWithEngine(engine)` for the native `systemOne` /
  `listModels`, and `liquidAsOpenAI()` - the Jev OpenAI adapter, `json_schema` structured output only (base
  `https://api.liquid.ai/decisions`, `LIQUID_API_KEY`, model `NonOpenAIModelId.liquid_d1_free` = `d1:free`, listed in
  `models-supporting-json-schema`).
- **Not yet - chat**: Liquid's OpenAI-compatible surface is `ChatProviderSettings.liquid`
  (`https://api.liquid.ai/openai/v1/`); it answers in OpenAI's format but lists no chat models for a free-tier key.
- **d1 vs Jev** (live 2026-09-30, the free tier): the answers agree and d1 bills no output tokens; d1 takes ~340 ms per
  call plus ~30 ms per question beyond three (1,517 ms at 40 questions) - and far more under load (medians up to 6.7 s
  in a later run) - Jev a flat ~240-270 ms. The free tier was
  intermittently unavailable on launch day (stretches of 429 `model_unavailable`, seconds-long answers right after one) -
  `TypeSafeServiceAdapters.retry` treats a 429 as transient.
- Liquid's error bodies come in OpenAI's shape; they are classified by status into the same `TypeSafeScala*Exception`s,
  and the exception's `errorType` now falls back to the body's `error.code` / `error.type` (e.g. `model_unavailable`).
- See `typesafe/LiquidD1SmokeTest`: the models, a native call, the OpenAI adapter, Jev side by side and a latency
  benchmark.

### ws-client 1.1.1 - classified streaming errors

- Upgraded to `io.cequence:ws-client-*:1.1.1`. A streamed request answered with a non-2xx status now fails the stream
  with a structured `CequenceWSHttpStatusException` (status + bounded body) instead of emitting the error body as stream
  data. Every streamed service now extends the new `ClassifiedStreamingWSClient` (openai-core) and streams through
  ws-client's service-level stream methods, which route it through the service's own `handleErrorCodes` - OpenAI chat /
  completions / Responses, Anthropic messages / batch results / Managed Agents session events / Bedrock, Gemini,
  Perplexity Sonar / Agent / Responses - so streamed calls fail with the same classified exceptions and `Retryable`
  verdicts as the non-streamed ones (e.g. a 429 is an `OpenAIScalaRateLimitException`, not an unclassified failure).
  `StreamErrorMappingConventionSpec` fails the build on any engine-level stream call left without the mapping. The
  OpenAI streamed services now mix in `HandleOpenAIErrorCodes`.
- **In-band stream errors classified**: an `{"error": ...}` frame inside a 200 stream (a mid-stream Anthropic
  `overloaded_error`, an OpenAI `server_error`, a Gemini `UNAVAILABLE`, a Perplexity `code: 429`) used to fail the stream
  with a plain, never-retried `OpenAIScalaClientException`; it is now classified like the HTTP status it stands for
  (from its numeric `code`, Google `status` or OpenAI / Anthropic `type` - `InBandStreamErrors`).
- The Gemini OpenAI adapter now also repacks errors raised DURING its streams onto `OpenAIScala*` (before only the setup
  future was repacked; Anthropic already did it). Pinned per provider against a local server:
  `StreamedHttpErrorsWireSpec` (OpenAI), `AnthropicStreamedHttpErrorsWireSpec` (direct API, batch results, session
  events, Bedrock, adapter), `GeminiStreamedHttpErrorsWireSpec` (native + adapter) and `SonarAgentWireSpec`
  (Perplexity).
- **Vertex AI errors classified by canonical status** (`vertexai/service/impl/VertexAIErrors`): the adapter maps every
  gRPC status (`RESOURCE_EXHAUSTED` -> rate limit, `UNAVAILABLE` -> overloaded, `DEADLINE_EXCEEDED` -> timeout,
  `INTERNAL` / `UNKNOWN` / `DATA_LOSS` -> server error, `UNAUTHENTICATED` / `PERMISSION_DENIED` -> unauthorized, a
  token-count `INVALID_ARGUMENT` -> token count exceeded, the rest -> client error) by status rather than by exception
  class, finds the gax / raw gRPC exception anywhere in the cause chain, and keeps it as the cause. The batch-prediction
  REST service, which had no error classification at all, now classifies Google's error bodies by `error.status`
  (falling back to the shared HTTP policy, now `OpenAIErrorCodes` in openai-core - `HandleOpenAIErrorCodes.toException`
  delegates to it) and maps transport timeouts / unknown hosts, so
  429 / 503 / 5xx are `Retryable`.
  Pinned end to end: `VertexAIStreamedErrorsSpec` (the real SDK over a scripted transport stub - failures at stream
  open, mid-stream after a delivered chunk, wrapped, and unary), `VertexAIBatchHttpErrorsWireSpec` (local server) and
  `VertexAIErrorsSpec` (every status).

### 🔥 Perplexity Agent API

`SonarService` gains Perplexity's Agent API (`/v1/agent`, `/v1/models`), the successor of the Sonar chat completions API:
`createAgentResponse` / `createAgentResponseStreamed` (typed `AgentStreamEvent`s; an SSE decoder that accepts LF and CRLF
framing and surfaces non-SSE error bodies), `retrieveAgentResponse` (404 -> `None`), `resumeAgentResponseStream`
(`starting_after` reconnect), `cancelAgentResponse`, `listAgentResponseFiles`, `downloadAgentResponseFile` and
`listAgentModels`. Typed inputs (text, message / function-call replay, image parts), every tool of the spec (web search
with filters and location, fetch URL, finance / people search, sandbox, custom functions, MCP, connectors, raw), skills,
profiles, presets, structured output, background mode; unknown output items and events are kept raw, never dropped.
Verified against Perplexity's OpenAPI document (vendored; every output item and event schema decoded in minimal and
maximal form, every written request validated) and the documented response examples, plus a local-HTTP wire spec.
Errors are a native hierarchy (`PerplexityScalaClientException`: `Unauthorized`, `InvalidRequest` / `InvalidModel`,
`NotFound`, `RateLimit`, `ClientTimeout`, `ServerError` / `EngineOverloaded`, `ClientUnknownHost`), classified by HTTP status
and Perplexity's `error.type` (bodies collected live), carrying `httpCode` / `errorType` / the `x-request-id` via
`ProviderErrorDetails`; `PerplexityRetryable` says which to retry, and a streamed request's error body is classified by its
`code` too. Live-verified 2026-09-25 (`PerplexityAgentApiSmokeTest`: models, web search run, structured output, streaming,
custom function round trip, multi-turn, background submit / retrieve / cancel, 404).
`PERPLEXITY_API_KEY` now works next to `SONAR_API_KEY`; `SonarServiceFactory` takes an optional `baseUrl`.

`SonarServiceFactory.agentAsOpenAI()` - an OpenAI chat-completion service (chat, function tools, JSON, typed and legacy
streaming, web search) on the Agent API through Perplexity's OpenAI-compatible `/v1/responses` alias: a small
Responses service in the Perplexity module behind the core `OpenAIResponsesChatCompletionService`; errors are the shared
`OpenAIScala*` exceptions with the Perplexity exception as the cause (so the retry adapters work). Live-verified
(`PerplexityAgentAsOpenAISmokeTest`).

### Changed
- **`createChatFunCompletion` works again** - the deprecated legacy `functions` API sends the function objects
  themselves; it sent the tools' `{"type": "function", "function": ...}` wrapper, which the API now rejects with a 400
  ("Missing required parameter: 'functions[0].name'", found by the 2026-09-30 example sweep). A non-function tool fails
  the returned Future.
- **Typed stream: a turn that streamed tool calls finishes as `tool_calls`** on every OpenAI-compatible provider, also
  when the provider answers `stop` - OpenAI itself does so for a forced `tool_choice` (live-verified 2026-09-29), as does
  Together AI's Muse Glimmer. The provider's value stays in `Finish.providerReason`; the non-streamed `finish_reason`
  is the provider's own text, as before.
- **Model routing and conversions re-measured** against the live chat completions API (41 OpenAI models x 23
  parameter cases, raw and through the client - `OpenAIConversionsAudit`), fixing every missing conversion found:
  per-family `reasoning_effort` mappings (gpt-5: `none`->`minimal`, `xhigh`/`max`->`high`; 5.1: `minimal`->`low`,
  `xhigh`/`max`->`high`; 5.2 / 5.4 / 5.5: `minimal`->`low`, `max`->`xhigh`; o-series: `none`/`minimal`->`low`, `max`->`xhigh`,
  logprobs dropped); 5.1 / 5.2 penalties now kept without reasoning (they were always zeroed); a `gpt-5-search-api` rule
  (temperature / top_p / penalties rejected even at defaults, reasoning_effort dropped); GPT-5 dispatched on the parsed
  minor version (`gpt-5.10` is no longer treated as 5.1, future minors get the 5.6 rules) and the o-series by pattern
  (future-proofing: the ids the old set missed are Responses-only or shut down); GPT-5.4 / 5.5 tool calls with an explicit
  effort go through the Responses API on the full service (chat completions rejects the combination), while `none` is
  kept; GPT-5.4 `logprobs` kept without reasoning except on the two dated snapshots that 403 it; tool calls on
  `gpt-5-search-api` (which supports none) fail fast with a clear error.
- **`stop` / `logit_bias` dropped where OpenAI rejects them** (measured 2026-09-30 on 37 chat models, raw and through the
  client): every GPT-5.x and GPT-6 model (with any `reasoning_effort`, `none` included), `chat-latest` and
  `gpt-5-search-api` reject both ("Unsupported parameter"), the o-series rejects `logit_bias` and - except `o1` /
  `o3-mini` - `stop`; they are now dropped with a warning instead of failing the call. Bedrock's OpenAI models reject
  `stop` too and accept `logit_bias` but ignore it (a +100 bias changes nothing), so it is dropped there as well. The
  non-reasoning models (gpt-3.5 / 4 / 4o / 4.1) keep both. `OpenAIConversionsAudit` gained the cases and an
  `AUDIT_CASES` filter.
- **Gemini thinking** re-measured per model (`GeminiThinkingAudit`): the rolling aliases (`gemini-flash-latest` etc.) and
  `nano-banana-pro*` now get thinking levels (reasoning_effort was dropped for them), the 3.1 Flash image models get only
  MINIMAL / HIGH, `gemini-2.5-flash-image` gets no thinking config.
- `models-supporting-json-schema`: added `gpt-4o-mini`, `gpt-5-search-api`, `gemini-2.5-flash-lite`, `grok-4.7`
  (registered too); the Bedrock cross-region spelling of an OpenAI model matches its bare id. The Anthropic max-output
  table resolves by the longest matching id. `gpt-4o(-mini)-search-preview` deprecated (404).

- Responses API reads are tolerant of Responses-compatible providers: output items of an unknown type are skipped with a
  warning instead of failing the whole response, and an unknown `truncation` value reads as `None` (Perplexity sends
  `search_results` items and `"truncation": ""`). Malformed items of known types still fail.

### Deprecated

- **Assistants API** - the 26 `OpenAIService` methods for assistants, threads, thread messages, runs and run steps are
  `@deprecated`: OpenAI shut the Assistants API down on 2026-08-26 (its endpoints now return 404). Use the Responses API
  (`createModelResponse`, `createModelResponseStreamed`) instead. Vector stores are unaffected. Streamed runs
  (`stream = true`, [#87](https://github.com/cequence-io/openai-scala-client/issues/87)) will not be added.
- **Dead and retiring models** - 225 model-id constants are `@deprecated`, each with its source and date:
  - OpenAI: every id with a `shutdown_date` in `/v1/models` (past or scheduled - e.g. `gpt-3.5-turbo-instruct`,
    `davinci-002`, `babbage-002` on 2026-09-28, `gpt-4*` / `gpt-3.5-turbo` / `o1*` / `o3-mini` / `o4-mini` /
    `gpt-image-1` on 2026-10-23, `whisper-1` on 2027-02-26) plus the ids OpenAI no longer serves at all (ada / babbage /
    curie / davinci, `dall-e-2/3`, `o1-preview/mini`, `gpt-4-32k`, `gpt-4.5-preview`, `text-moderation-*`, ...)
  - Anthropic: the retired Claude 2 / Instant / 3 / 3.5 / 3.7 / Opus 4 / Sonnet 4 / Opus 4.1 ids (retirement dates from
    Anthropic's deprecation page) and the Bedrock ids no longer in its catalog
  - Gemini API: the 1.0 / 1.5 / 2.0 families and the dated 2.5 preview / experimental ids (404 on 2026-09-25)
  - Perplexity: `sonar-reasoning`, `r1-1776`, `llama-3.1-sonar-*`
- **Dead and retiring endpoints** - `createEdit` (gone), `createImageVariation` (gone with dall-e-2),
  `createAudioTranslation` (whisper-1 only, shuts down 2027-02-26), `createCompletion` on `OpenAIService` (OpenAI's
  last completions models shut down 2026-09-28; `OpenAICoreService` keeps it for OpenAI-compatible servers), and the
  Sonar chat completions calls - `SonarService.createChatCompletion(Streamed)` and `SonarServiceFactory.asOpenAI`
  (Perplexity ends that API on 2026-09-27; use the Agent API above).
- **dall-e-only image options** - `ImageSizeType.Small` / `Medium` / `LargeLandscape` / `LargePortrait` and
  `ImageQualityType.standard` / `hd`; new `gpt-image` values `ImageSizeType.Landscape` / `Portrait` / `Auto` and
  `ImageQualityType.low` / `medium` / `high` / `auto`.

### Changed defaults

- `createImage` / `createImageEdit` default to `gpt-image-2` (the API no longer picks a model, dall-e is shut down).
- `createChatWebSearchCompletion` defaults to `gpt-5-search-api` (`gpt-4o-search-preview` is shut down).
- `createAudioSpeech` defaults to `gpt-4o-mini-tts` (was `tts-1-1106`), `createAudioTranscription` to `gpt-transcribe`
  (was `whisper-1`).
- Examples moved off every retired model; `CreateEdit` was removed and `CreateChatCompletionWithO1` became
  `CreateChatCompletionWithO3`.

## 1.3.0 (2026-09-18)

292 commits since v1.2.0 (2025-04-23), 646 files, +69k lines. Three release candidates along the way
(RC.1 2025-11-05, RC.2 2026-03-06, RC.3 2026-06-11). Everything below is live-verified against the
providers unless stated otherwise.

Artifacts (Scala 2.12 / 2.13 / 3): `openai-scala-client`, `openai-scala-client-stream`,
`openai-scala-anthropic-client`, `openai-scala-google-gemini-client`, `openai-scala-google-vertexai-client`,
`openai-scala-perplexity-sonar-client`, `openai-scala-typesafe-client` (new), `openai-scala-claude-agent-client` (new),
`openai-scala-count-tokens`, `openai-scala-guice`, and the new envelope `openai-scala-all`.

---

### 🔥 TypeSafe AI System One (`Jev`) - new `typesafe-client` module

A client for a decision model, not a chat model. You send a `state` (text or JSON) plus named, typed
questions and get typed answers with calibrated probabilities in ~100 ms.

- `TypeSafeService.systemOne(state, questions)` with `ChoiceQuestion` (one of a fixed set),
  `ScoreQuestion` (a level on an ordered rubric) and `NoulQuestion` (yes/no); typed `ChoiceAnswer` /
  `ScoreAnswer` / `NoulAnswer` (probabilities, confidence, `ranked`, `mostLikelyLevel`, expected score);
  unknown answer types arrive as `UnknownAnswer`. `listModels`. Env `TYPESAFE_API_KEY`
  (optional `TYPESAFE_BASE_URL`, `TYPESAFE_DEFAULT_MODEL`), same as the official SDKs.
- Model ids `NonOpenAIModelId.jev_latest` / `jev_preview` / `jev_1_13_0` with pricing ($0.042 / 1M input
  tokens, output free), the ~32k-token input limit and the rate limits documented on the constants.
- **OpenAI adapter** `TypeSafeServiceFactory.asOpenAI()` - an `OpenAIChatCompletionService` for
  `json_schema` structured output only: the schema becomes the questions (boolean → noul, string enum →
  choice, numeric enum or a small `minimum`..`maximum` range → score, array of string enum →
  multi-select, nested objects), the messages become the state (`TypeSafeChatMapping.toState` /
  `toQuestions` preview it), the assistant content is a JSON document of the schema, and the
  `SystemOneResponse` rides in `originalResponse`. Works unchanged with `createChatCompletionWithJSON`,
  routers, retry, logging and interception adapters. Only `model`, `response_format_type`, `jsonSchema`
  and `n = 1` are honoured; every other setting is dropped with one warning naming it; free-form
  strings, plain requests, `n > 1`, tools, streaming and images are refused before any I/O.
  `setTypeSafeNoulThreshold` tunes the yes/no cut-off.
- Native exception hierarchy `TypeSafeScalaClientException` classified by status and body
  (`Unauthorized`, `TokenCountExceeded`, `ApiUsage`, `InvalidRequest` with `violations`, `NotFound`,
  `ClientTimeout`, `RateLimit`, `EngineOverloaded`, `ServerError`), each carrying `httpCode`,
  `errorType` and the `x-typesafe-request-id`; `TypeSafeRetryable` + `TypeSafeServiceAdapters.retry`;
  repacked to `OpenAIScala*` by the adapter with the native exception as the cause.
- `JsonSchema.Integer` / `Number` gained `minimum`, `maximum` and `enum` (core). OpenAI honours them in
  strict mode, Anthropic honours `enum` and the adapter strips the bounds (Anthropic 400s on them),
  Gemini / Vertex AI read only the description.
- `TypeSafeServiceFactory.withEngine(engine)` shares one HTTP engine with the other providers. Wire format
  pinned against the published OpenAPI spec and the Python SDK fixtures; 84 tests; live smoke test,
  15-scenario adapter walkthrough and a message-mapping benchmark under `examples/typesafe`.

### 🔥 Typed streaming and unified tool calls (`ChatChunk`)

One provider-neutral event stream for text, thinking, tool calls and tool results.

- `createChatToolCompletionStreamed(messages, tools, responseToolChoice, settings)` and the tool-less
  alias `createChatCompletionStreamedTyped` return `Source[ChatChunk, NotUsed]`. The sealed ADT
  (`domain/response/ChatChunk.scala`): `Start`, `Text`, `Thinking`, `ThinkingSignature`,
  `RedactedThinking`, `ToolCallStart` / `ToolCallDelta` / `ToolCall`, `ToolResult` (`serverSide` flag),
  `CodeExecution(Result)`, `WebSearch(Result)`, `Image`, `Refusal`, `Citation`, `Finish`, `Usage`,
  `Other(kind, raw)` (nothing is dropped), plus the control events `Retry(attempt, model)` and `Done`.
  `source.assembled` folds into `AssembledChatCompletion`; `source.texts` is the legacy string view.
- Native mappers: OpenAI and every OpenAI-compatible provider via `ChatChunks.fromOpenAIChunks`
  (handles `delta.tool_calls`, `reasoning_content` / `reasoning`, deduplicates repeated `usage`;
  live-verified on Grok, Groq, Cerebras, Fireworks, DeepSeek); Anthropic (thinking with
  `display = summarized`, server-tool results, MCP connector, `pause_turn` continuation); Gemini
  (thoughts, function calls, code execution, grounding, server-executed MCP); Vertex AI (protobuf parts,
  `setVertexAIIncludeThoughts`).
- **Responses API streaming**: `createModelResponseStreamed(inputs, settings)` returns
  `Source[ResponseStreamEvent, NotUsed]` (typed SSE events, unknown ones as `UnknownEvent`) and
  `createModelResponseStreamedTyped` maps them to `ChatChunk`. `OpenAIResponsesChatCompletionService`
  runs chat completions over the Responses API (`responsesAsChatCompletion`,
  `createChatToolCompletionStreamedViaResponses`); GPT-6 function tools are routed there automatically.
- **Provider-neutral MCP servers and skills**: `ChatCompletionTool.MCPServerTool` and
  `ChatCompletionTool.SkillTool` go in `tools` next to `FunctionTool`. OpenAI routes them through the
  Responses API (`mcp` tool, skills as one hosted `ShellTool` container); Anthropic maps them to
  `mcp_servers` and `container.skills` + code execution; Gemini to one `mcpServers` tool; Vertex AI refuses
  them explicitly. Chat-only services fail fast with an explanatory exception.
- Anthropic SSE frames are now capped at 1 MB (ws-client's 20 KB default broke web-search result blocks);
  Gemini streams as real SSE (`alt=sse`, CRLF framing); Sonar's frame cap raised likewise.
- Deprecated: the `<think>`-tag filtering helpers in `MessageConversions` - use `reasoningText` /
  `ChatChunk.Thinking`.

### 🔥 Unified batch processing

Provider-agnostic batches at ~50% of the standard cost, with the same API on every provider that has one.

- `OpenAIChatCompletionBatchService`: `createChatCompletionBatch`, `getChatCompletionBatch`,
  `retrieveChatCompletionBatchResults`, `cancelChatCompletionBatch`, `deleteChatCompletionBatch`
  (mixed into `OpenAIService`). Domain `ChatCompletionBatchRequest`, `ChatCompletionBatchInfo`,
  `ChatCompletionBatchStatus`, `ChatCompletionBatchResultItem` / `ChatCompletionBatchTypedResultItem[T]`.
- Implemented natively for OpenAI (Batch API), Anthropic (Message Batches), Anthropic on Bedrock
  (batch inference jobs staged through S3, `bedrockAsOpenAIWithBatchSupport`), Gemini (Batch Mode with
  automatic JSONL upload through the Files API) and Vertex AI (batch prediction jobs staged through Cloud
  Storage, `asOpenAIWithBatchSupport`).
- `chatCompletionBatchEmulated(service)` runs batches as parallel synchronous calls for providers without
  a batch endpoint. Routers: `chatCompletionBatchRouter`, `chatCompletionBatchRouterMapped` and the
  mixed variants that combine batch-capable and chat-only providers in one map.
- Typed helpers: `createChatCompletionBatchAndWaitForResults` (polling + cleanup) and
  `createChatCompletionBatchWithJSON[T]` with model failover; `OpenAIScalaBatchTimeoutException`
  (deliberately not retryable); `chatCompletionInputWithBatch` batch-preserving input adapter;
  `extra_params` spread into the JSONL bodies. Live VLM smoke tests across all batch paths.

### 🔥 HTTP engine sharing and the multi-backend groundwork (ws-client 1.0)

- Migrated to `io.cequence:ws-client-*:1.0.0` (a Maven Central release, no SNAPSHOT). Engines are now
  **site-stateless**: an engine is the HTTP client + pool + actor system, each service holds a
  `SiteBinding` (base URL, auth, error taxonomy). Every service created via the plain factories owns a
  private engine and closes it with `service.close()`; the actor system's threads are daemon.
- **One engine for many services, across providers**: `StreamedEngineRegistry.outputStreamed()` plus
  `withEngine(engine)` on `OpenAIServiceFactory`, `AnthropicServiceFactory`, `GeminiServiceFactory`,
  `SonarServiceFactory`, `TypeSafeServiceFactory`, `VertexAIServiceFactory.batchPredictionWithEngine`
  and the Bedrock variants. A service on a shared engine never closes it; `engine.close()` is the one
  teardown. `engine.copy(TransportSettings(...), reuseExecContext = true)` gives a service different
  timeouts on the same actor system.
- Timeouts are client-level (`TransportSettings(timeouts)`, every factory takes
  `timeouts: Option[Timeouts]`, milliseconds; the `*Sec` config keys are seconds). A caller-supplied
  `Materializer` is no longer accepted or needed by any factory, including the Guice provider.
- **Akka / Pekko readiness**: the artifacts are now the Akka-flavoured `ws-client-core-akka`,
  `ws-client-play-akka`, `ws-client-play-akka-stream`; ws-client also ships Pekko engines and backend-only
  JDK / sttp engines behind the same SPI. Synchronous calls already run unchanged on the Pekko engine
  (verified); streaming is still pinned to `akka.NotUsed` / `Source` in this repo's public API and will be
  abstracted in a later release. Embedding in an existing Akka app: build the engine on your materializer
  and pass it to `withEngine`.
- Engine selectable with `-Dws-client.engine=<id>`; `apiKey` config is optional with a descriptive
  failure at use time.

### 🔥 Claude Agent Client - new `claude-agent-client` module

- Wraps the `claude` CLI as a subprocess (NDJSON over stdin/stdout, Claude Agent SDK protocol) so an
  application can drive Claude Code sessions and bill against a Claude subscription rather than API tokens.
- `ClaudeAgentServiceFactory.startSession(settings)` → `ClaudeAgentService`: `ready`, `events`
  (`Source[ClaudeAgentEvent, NotUsed]`, BroadcastHub-backed so it can be subscribed more than once),
  `send`, `sendToolResult`, `respondToolPermission` (`PermissionDecision.Allow/Deny`), `interrupt`,
  `sendControlRequest`, `completion`, `sessionId`. Events: `SystemInit`, `Assistant` (reusing the
  Anthropic content-block codecs), `UserEcho`, `ResultSuccess` / `ResultError`, `StreamDelta`,
  `ToolPermissionRequest`, `Unknown` (each with the raw JSON).
- `ClaudeAgentSettings` covers model, system prompt (set or append), allowed / disallowed tools,
  permission mode, max turns, cwd, resume / continue / fork session, partial messages, executable path,
  env and extra args. Requires the `claude` CLI installed and authenticated (subscription login or
  `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` / `CLAUDE_CODE_OAUTH_TOKEN`).

### Anthropic Managed Agents

- The full Managed Agents REST API (beta `managed-agents-2026-04-01`) on `AnthropicService`: agents (+
  versions), environments (+ the self-hosted work queue: poll, acknowledge, heartbeat, stop, stats),
  sessions (events incl. SSE `streamSessionEvents`, resources, threads), deployments (+ runs, pause /
  unpause), vaults, credentials (incl. MCP OAuth validation), memory stores (memories + versions +
  redaction). Domain package `anthropic.domain.managedagents`, per-method `@see` doc links.
- `managedAgentAsOpenAI` / `managedAgentAsOpenAIWithAuthToken` expose an agent as an
  `OpenAIChatCompletionService`. Not available on Bedrock.

### Anthropic client

- **Auth**: API key; static bearer / OAuth tokens (`forAuthToken`, `asOpenAIWithAuthToken`;
  `ANTHROPIC_AUTH_TOKEN` → `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE` → `CLAUDE_CODE_OAUTH_TOKEN`);
  `ant auth` profiles with automatic refresh (`forOAuthProfile`); `forAuthTokenProvider`; and
  `customInstance(coreUrl, requestContext)` for Anthropic-compatible endpoints (e.g. MiniMax).
- **Bedrock**: SigV4 (`forBedrock`, with `inferenceProfilePrefix` for `eu.` / `us.` cross-region
  profiles), STS session tokens minted in-process (`forBedrockWithSessionToken`), Bedrock API keys
  (`forBedrockWithBearerToken`), Bedrock Mantle (`forBedrockMantle`), batch inference jobs, structured
  outputs relocated into `output_config.format`. Live-verified 2026-09-18: Bedrock accepts structured
  outputs only for the Claude 4.5 / 4.6 profiles; Opus 4.7+, Sonnet 5, Opus 5 and the 5.x family reject
  them, so those ids are deliberately not in `models-supporting-json-schema` (the JSON helper falls back to
  prompt mode). Bedrock rejects `mcp_servers`, Files, Skills, Message Batches and Managed Agents with
  explicit exceptions.
- **Messages API**: `createMessageStreamedEvents` (typed `MessageStreamEvent` SSE stream); Message
  Batches; Files API; Skills API (+ versions); per-model default `max_tokens` (128k on the 5.x / 4.6+
  families); default model `claude-haiku-4-5`.
- **Thinking and effort**: `ThinkingSettings` `enabled(budget)` / `adaptive` / `adaptiveSummarized` with
  `display = summarized | omitted | updates`; `OutputConfig(effort)` with `low` … `xhigh` / `max`;
  OpenAI `reasoning_effort` mapped per model family (adaptive-only models switch automatically, `xhigh`
  downgraded where rejected, legacy models via the configurable thinking-budget mapping); fast mode
  (`speed`, `setAnthropicFastSpeed`).
- **Tools and content**: bash, code execution, computer use, custom, memory, text editor, web search,
  web fetch, MCP connector (`mcp_servers`, `MCPToolset`), skills containers; `ToolChoice`; content blocks
  for tool use / results, server tools, MCP, container uploads, code-execution results, documents by
  `file_id`; citations as a sealed hierarchy; prompt caching with `CacheTTL` 5m / 1h; provider-uniform
  `FileContent` / `VLMContent` attachments (PDF, images).
- **OpenAI adapter**: `json_schema` structured output (numeric bounds stripped with a warning), typed
  `ChatChunk` streaming, `setAnthropicTools`, `setAnthropicMcpServers`, transparent `pause_turn`
  continuation capped by `setAnthropicMaxContinuations` (default 6), raw `CreateMessageResponse` in
  `originalResponse`, batch adapter.
- Error mapping: 403 → unauthorized, 408 → client timeout, 429 → rate limit, 503 / 529 → engine
  overloaded, any other 5xx → retryable server error; token-count and max-output phrases detected.

### OpenAI client and core

- **Service traits**: `OpenAIService` is now `OpenAICoreService with OpenAIResponsesService with
  OpenAIGraderService with OpenAIChatCompletionBatchService`; `createChatToolCompletion` moved down to
  `OpenAIChatCompletionService` with `tools: Seq[ChatCompletionTool]`.
- **Responses API**: `cancelModelResponse`, `getModelResponseInputTokenCounts`,
  `listModelResponseInputItems`; tools remodeled with shortcuts (`Tool.function / fileSearch / webSearch
  / computerUse / codeInterpreter / imageGeneration / localShell / custom / customWithGrammar / mcp /
  mcpWithConnector`), MCP package (`MCPTool`, `MCPToolCall`, `MCPListTools`, approval request /
  response, `MCPToolError`), hosted `ShellTool`, `Input.of*` factories for every call / output kind,
  `CreateModelResponseSettings` gained `prompt`, `promptCacheKey`, `background`, `maxToolCalls`,
  `safetyIdentifier`, `serviceTier`, `streamOptions`, `topLogprobs`; `toTracedBlocks`.
- **Graders API**: `runGrader`, `validateGrader`; `StringGrader`, `TextSimilarityGrader`,
  `LabelModelGrader`, `ScoreModelGrader`, `PythonGrader`, `MultiGrader`.
- **Amazon Bedrock for OpenAI models** (`bedrock-mantle` and the classic `bedrock-runtime` endpoint):
  one entry point `OpenAIServiceFactory.forBedrock(auth, region, endpoint)` with `BedrockAuth.BearerToken`
  / `BedrockAuth.SigV4` and `BedrockEndpoint.Mantle` / `Runtime`; SigV4 signer and credentials provider in
  core (`aws/AwsSigV4`, `AwsCredentialsProvider`, per-request resolution for rotating STS / IRSA
  credentials); `forAwsSigV4Custom` for other signed endpoints; Bedrock-hosted OpenAI ids in `ModelId`
  with the per-model parameter rules applied to the prefixed ids.
- **Per-model settings conversions** (`ChatCompletionSettingsConversions`): GPT-5, 5.1, 5.2, 5.3, 5.4,
  5.5, 5.6 (Sol / Terra / Luna / Cyber), GPT-6 Astra, `chat-latest`, o-series, Groq; `reasoning_effort`
  tiers `none`, `minimal`, `xhigh`, `max` with the documented downgrades; `verbosity`; `ServiceTier.flex`;
  GPT-6 function tools routed to the Responses API.
- **Adapters**: `chatCompletionRouter[T]` / `chatCompletionRouterMapped[T]` keyed by service subtype,
  `chatCompletionIntercept` (with `adjustSettingsForCall`), `chatCompletionErrorIntercept` with timing,
  `chatCompletionInput` / `chatCompletionOutput`, retry with `includeExceptionMessage` and `jitterMs`,
  failover with model filtering. `FutureWithRetry` now takes the future by name so retries re-run the call.
- **JSON helpers**: `createChatCompletionWithJSONFullResponse`, `createChatCompletionWithFailoverSettings`,
  `toStrictSchema`, `JsonSchema.setAdditionalPropertiesToFalse`; billed usage preserved on JSON parse
  failures (`OpenAIScalaJsonParseException` carries the response); `models-supporting-json-schema` moved
  into the config file (~250 ids across providers) with a `reasoning-effort-thinking-budget-mapping`
  block for Gemini and Anthropic budgets.
- **Domain**: `CreateChatCompletionSettings` builders (`withTemperature`, `withMaxTokens`, …),
  `FileContent` + `VLMContent` (files, images incl. BMP / TIFF, base64, `[file #N: NAME]` labels),
  `ChatCompletionResponse.originalResponse`, speech `instructions` / `stream_format` / new voices,
  `HasType`, `ProviderErrorDetails` (httpCode / errorType / requestId on provider exceptions, walks the
  cause chain), `OpenAIScalaCapacityExceededException` (498), any 5xx retryable, null assistant content
  tolerated, legacy `function_call` assistant messages kept.
- **Model ids**: OpenAI GPT-5 … GPT-5.6, GPT-6 Astra, o3-pro, deep-research, audio / realtime /
  transcribe, gpt-image-1 / 1.5 / 2 / 2.5, Sora 2; Claude 4 … Opus 5 / Sonnet 5 / Fable 5.x / Mythos
  5.x (direct + Bedrock EU / US); Gemini 2.5 / 3 … 3.8 with rolling aliases, Gemma 4; Grok 4 … 4.6 and
  Build; DeepSeek V3.1 / V4 / V4.1 Flash; Groq, Cerebras, Fireworks, Together, Novita, SambaNova,
  Mistral 2026 listing, Zhipu GLM 4.6 … 5.2, MiniMax M2.x / M3 (direct provider settings `minimax` /
  `minimaxChina` contributed in #127), Moonshot Kimi K2, Qwen 3.x, gpt-oss; TypeSafe `jev-*`. The GPT-5.3
  ids that never shipped were removed.

### Google Gemini and Vertex AI

- Gemini: Batch Mode and Files API; `ThinkingConfig` with `thinkingBudget` / `thinkingLevel` (`MINIMAL`
  … `HIGH`) and `includeThoughts`; tools reachable through the adapter (`setGeminiTools`,
  `setGeminiToolConfig`, `FunctionCallingMode`); **server-executed MCP servers** (`Tool.McpServers`,
  `McpCallRule`, dangling calls surface as `GeminiScalaMcpCallNotExecutedException`, retryable);
  `x-goog-api-key` header auth; tolerant parsing (blocked candidates, unknown enums, `Part.Unknown`);
  a `GeminiScalaClientException` hierarchy with status-code mapping repacked by the adapter; usage
  accounting folds thoughts and tool-use prompt tokens; typed `ChatChunk` streaming with
  `setGeminiIncludeThoughts`; batch adapter with shared system caches; default model `gemini-3.6-flash`.
- Vertex AI: `google-cloud-vertexai` 1.52.0; tools (`setVertexAITools`, `setVertexAIToolConfig`);
  `reasoning_effort` → thinking budget; typed streaming over the Java SDK parts (unit-tested only);
  assistant tool messages and tool results forwarded, `logprobs` and `seed` wired; gax exceptions mapped
  to the OpenAI hierarchy; batch prediction jobs staged in Cloud Storage; `n` → candidate count.

### Perplexity Sonar

- Shared-engine factory, 1 MB frame cap. **Note**: Perplexity retires the Sonar chat-completions
  endpoint on 2026-09-27 in favour of the Agent API; this release still targets chat completions.

### Count tokens, Guice, envelope

- `openai-scala-all` aggregates every client module (streaming, Anthropic, Gemini, Vertex AI, Perplexity,
  TypeSafe, Claude Agent, count-tokens).
- Count tokens: function-call serialization over the typed `JsonSchema` (enums, nested descriptions),
  one shared jtokkit registry per JVM, `o200k_base` routing for the GPT-4.1+ / 5.x / 6 / o-series families
  with a warned fallback for unknown ids.
- Guice: `OpenAIServiceProvider` needs only an `ExecutionContext`.

### Other

- Weekly Scala Steward dependency updates; default `scalaVersion` moved to 2.13; LICENSE range extended
  to 2026; README restructured (provider table with live-probed JSON-schema support, engine sharing,
  typed streaming, batches, TypeSafe AI, Managed Agents, Claude Agent Client).

---

### Breaking changes

- **Service traits grew**: `OpenAIService` (Responses, graders, batches), `AnthropicService`
  (batches, files, skills, Managed Agents, `createMessageStreamedEvents`), `GeminiService` (batches,
  files). Third-party implementations must add the new methods; callers are unaffected.
- `createChatToolCompletion` moved from `OpenAIService` to `OpenAIChatCompletionService`; `tools` is
  `Seq[ChatCompletionTool]`.
- `AssistantTool.FunctionTool.parameters` is a `JsonSchema` (was `Map[String, Any]`);
  `JsonSchema.Object(Map, …)` renamed to `ObjectAsMap`; `JsonSchemaOrMap` deprecated.
- ws-client 1.0: artifacts renamed to the `-akka` variants; `ProjectWSClientEngine.apply` takes
  `TransportSettings` only; `PlayWSClientEngine` / `PlayWSStreamClientEngine` no longer used; no implicit
  `Materializer` on any factory (`OpenAIServiceFactory`, `AnthropicServiceFactory`, `GeminiServiceFactory`,
  `SonarServiceFactory`, `VertexAIServiceFactory`) or on the Guice provider; timeouts moved out of
  `WsRequestContext` into `TransportSettings`.
- `CompletionTokenDetails` fields are all `Option` with defaults; `UsageMetadata` (Gemini) gained fields in
  the middle; `Part.Text` / `Part.FunctionCall` (Gemini) gained `thought` / `thoughtSignature` - pattern
  matches on positional arity must be updated.
- Anthropic: `CacheControl.Ephemeral` is a case class with `ttl`; `Citation` is a sealed hierarchy;
  `ContentBlock` / `DeltaBlock` extend `HasType`; `SourceContentBlockRaw` lost `type`; default message model
  `claude-haiku-4-5` and per-model default `max_tokens`; `AnthropicCreateMessageSettings` gained fields
  (use named arguments).
- `OpenAIServiceConsts`: `configPrefix` / `configFileName` moved to `HasOpenAIConfig`; default models are
  now `gpt-5.4-mini`. Gemini default model `gemini-3.6-flash`; Gemini auth via header; Gemini streaming is
  SSE.
- Error mapping: 403 → unauthorized, 408 → timeout, every 5xx → retryable server error (OpenAI, Anthropic).
- `RetryHelpers.FutureWithRetry` takes the future by name.
- Removed: the local repack-exception adapter (ws-client provides it), the GPT-5.3 ids that never shipped.

### Deprecations

- `MessageConversions` `<think>`-tag filtering; `ReasoningConfig`'s pre-`summary` field;
  `JsonSchemaOrMap`; the `context-1m-2025-08-07` Anthropic beta header (retired 2026-04-30).
- Perplexity Sonar chat completions (external, 2026-09-27).

---

## 1.2.0 (2025-04-23)

See the [GitHub release](https://github.com/cequence-io/openai-scala-client/releases/tag/v1.2.0).
