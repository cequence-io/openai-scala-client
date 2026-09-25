# Changelog

## Unreleased

### New models

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

See `GPT6SolLunaOpus55SmokeTest` for a live walkthrough.

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
`openai-scala-perplexity-client`, `openai-scala-typesafe-client` (new), `openai-scala-claude-agent-client` (new),
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
