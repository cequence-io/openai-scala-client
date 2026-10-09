# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **OpenAI Scala Client** - an async Scala client for OpenAI API and multiple LLM providers. It's a multi-module Scala project that supports Scala 2.12, 2.13, and 3, providing comprehensive coverage of OpenAI endpoints plus adapters for Anthropic, Google (Gemini/Vertex AI), Perplexity, and other LLM providers.

The library is designed to be self-contained with minimal dependencies and uses a Play WS backend for HTTP calls. It's published as `io.cequence:openai-scala-client` on Maven Central.

**Documentation layout** (since 2026-10-09): `README.md` is an overview and index - the provider tables, installation, config, a quick start and the page list; everything else lives in `docs/`, one page per topic (`providers.md`, `chat-completions.md`, `responses-api.md`, `agents-api.md`, `streaming.md`, `decision-models.md`, `adapters.md`, `http-engine.md`, `graders-api.md`, `anthropic-managed-agents.md`, `claude-agent-client.md`, `faq.md`), split verbatim from the former 2,450-line README. A feature's documentation goes on its page; the README gets at most a table row or an index line. Links from `docs/` to sources are `../openai-...`; the decision-model anchors (`decision-models.md#typesafe-ai-jev-` etc.) are GitHub slugs of the page's `##` headings.

**Versioning**: the latest release is **1.4.0** (Maven Central 2026-09-30, tag `v1.4.0`); the build is `1.4.1-SNAPSHOT`. If the changes since the last release are not binary compatible with it (new case-class fields, new abstract methods on the service traits, new factory parameters - compare the published jars' public signatures with `javap`), the next release is a minor one: 1.3.1-SNAPSHOT shipped as 1.4.0. New `@deprecated` annotations use the upcoming release as their `since` value. `CHANGELOG.md` has one section per release, newest on top, written when the release is prepared; it opens with the upgrade notes (API breaks and behavior changes against the previous release). The GitHub release (tag `vX.Y.Z` on the published commit) carries a shorter form of the same notes.

## Build & Test Commands

The project uses SBT with custom command aliases defined in build.sbt:

### Core Commands
- **Build & test**: `sbt clean test` or `sbt ++2.13.11 clean test` for a specific Scala version
- **Test with coverage**: `sbt testWithCoverage` (alias for `coverage; test; coverageReport`)
- **Format code**: `sbt formatCode` (runs scalafmt, scalafmtSbt, and Test/scalafmt)
- **Validate code**: `sbt validateCode` (runs scalafix and scalafmt checks - this is what CI runs)
- **Compile specific module**: `sbt core/compile` or `sbt client/compile` etc.
- **Run specific test**: `sbt "testOnly *YourTestClassName"`
- **Run example**: `sbt "examples/runMain io.cequence.openaiscala.examples.YourExampleClass"`

### Cross-Build
The project cross-compiles for Scala 2.12.18, 2.13.11, and 3.2.2:
- `sbt ++2.12.18 test`
- `sbt ++2.13.11 test`
- `sbt ++3.2.2 test`

### Running Examples
The `Example` trait uses `System.exit()` which sbt's TrapExit mechanism intercepts, causing output to be swallowed. For reliable output when running examples from sbt, either:
- Run from IntelliJ directly (recommended)
- Write standalone `main` methods using `Await.result` instead of extending `Example`
- Or run the compiled class on a plain JVM: `sbt "export examples/Runtime/fullClasspath"` prints the classpath (its last line), then `java -cp "<classpath>" io.cequence.openaiscala.examples.CreateChatCompletion` - an `Example` exits 0 on success and 1 on failure, so a script can sweep many. Run them from a scratch directory (some write files, e.g. `CreateAudioSpeech`).

The router examples (`CreateChatCompletionWithRouter`, `adapters/ChatCompletion*Router*`, `ChatCompletionProvider.octoML`) still route to OctoAI (shut down in 2024), a local Ollama and Azure Cohere, so they fail without those; the routers themselves were live-verified on 2026-09-30 across OpenAI, Groq, Anthropic and Gemini (sync, OpenAI-shaped and typed streams).

## Module Architecture

The codebase is organized into multiple SBT subprojects with clear dependency relationships:

### Core Modules
- **openai-core** (`core`): Core domain models, JSON formats, service interfaces (OpenAIService, OpenAICoreService, OpenAIChatCompletionService), adapters, retry helpers, and base exception types. This is the foundation that other modules depend on.
- **openai-client** (`client`): Main client implementation with factories (OpenAIServiceFactory, OpenAIChatCompletionServiceFactory) and concrete service implementations. Depends on and aggregates openai-core.
- **openai-client-stream** (`client_stream`): Streaming extensions providing OpenAIStreamedServiceExtra and streaming factories for SSE-based completions. Depends on openai-client.

### Provider-Specific Clients
- **anthropic-client** (`anthropic_client`): Anthropic/Claude API client with OpenAI-compatible adapter. Structured output drops `minimum` / `maximum` from numeric schema properties (Anthropic 400s on them; `enum` is passed through). On Bedrock, structured output rides in `output_config.format`, and support differs per INFERENCE PROFILE (re-probed 2026-10-07 in eu-central-1 / us-east-1, every profile 5-13 calls): the Claude 4.5 / 4.6 profiles (haiku-4-5, sonnet-4-5, opus-4-5, sonnet-4-6, opus-4-6) and Haiku 5.5 accept it everywhere (Haiku 5.5 despite Anthropic's docs, which say Bedrock has no structured outputs); Sonnet 5.5 only on `global.` / `eu.` (`us.` 400s), Opus 5.5 only on `eu.` (`global.` 400s, `us.` flapped 3 of 5 - AWS is rolling it out); opus-4-7, opus-4-8, sonnet-5, opus-5 and fable/mythos 5.x return 400 `output_config.format: Extra inputs are not permitted` (beta flag or top-level `output_format` make no difference). `models-supporting-json-schema` lists exactly the accepting profile ids (a bare Bedrock id only for Haiku 5.5, since a bare id stands for any profile via `inferenceProfilePrefix`); a listed id that 400s would fail the call, an unlisted one gets prompt-based JSON-object mode - re-probe before listing more. Fable 5 exists in the EU only as `global.anthropic.claude-fable-5`. Claude Opus 5.5 (`claude-opus-5-5`, live-verified 2026-09-22 on the Claude API and Bedrock `eu.`/`us.`/`global.` profiles) is adaptive-only like Opus 5 and additionally rejects `thinking.type = disabled` (the adapter never sends it - `reasoning_effort=none` omits thinking) and forced `tool_choice` `any`/`tool` like Fable 5.1 (downgraded to `auto` + a system instruction via `forcedToolChoiceUnsupportedModels`); NOTE `"claude-opus-5"` is a substring of `"claude-opus-5-5"`, so the `contains`-matched model sets must list Opus 5 and 5.5 deliberately. Claude Sonnet 5.5 (`claude-sonnet-5-5`, live-verified 2026-09-29; on Bedrock `global.anthropic.claude-sonnet-5-5`, plus `eu.` / `us.` since October 2026) is adaptive-only like Sonnet 5 (effort low..max, sampling params and `thinking.type = enabled` rejected, 128k output, `json_schema` on the Claude API, `output_config.format` rejected on Bedrock) but, like Opus 5.5, rejects forced `tool_choice` (in `forcedToolChoiceUnsupportedModels`) and `thinking.type = disabled`: its lowest setting is `thinking.type = between_tools` (no up-front thinking, only progress updates between tool calls; effort high or below; NO other field - a `display` is a 400), which the adapter sends for `reasoning_effort = none` (`betweenToolsThinkingModels`) and the typed stream leaves without the `display: summarized` it adds to adaptive / enabled thinking; Opus 5.5 / Sonnet 5 reject `between_tools`. Again `"claude-sonnet-5"` is a substring of `"claude-sonnet-5-5"`, so Sonnet 5.5 inherits Sonnet 5's set memberships - list it deliberately and never add Sonnet 5 to a set Sonnet 5.5 must not be in. Live walkthrough: `examples/anthropic/ClaudeSonnet55SmokeTest` (the Bedrock leg needs the bearer token - `AWS_BEARER_TOKEN_BEDROCK`). Claude Haiku 5.5 (`claude-haiku-5-5`, released and live-verified 2026-10-07 on the Claude API; $0.10 / $0.50 per MTok, 1M context, 128k output) is adaptive-only (`enabled` and `between_tools` are 400s; sampling params pass only at their defaults - temperature 1 / top_p 0.99 - so the adapter drops them; no assistant prefill; no fast mode - `speed` is a 400) with effort low..max, json_schema on the Claude API, and it KEEPS forced `tool_choice` (`any` / `tool` work - it is NOT in `forcedToolChoiceUnsupportedModels`). It thinks by default, so `reasoning_effort = none` must be sent explicitly: `thinking.type = disabled` (accepted at effort high or below; xhigh / max + disabled are 400s) - the adapter's `noneThinkingByModel` maps none to Sonnet 5.5's `between_tools` and Haiku 5.5's `disabled`. On Bedrock (live 2026-10-07, both auth modes, eu-central-1 / us-east-1 / eu-north-1 through the client) it needs an inference profile - `global.` everywhere, `eu.` from EU regions, `us.` from US ones; the bare `anthropic.claude-haiku-5-5` 400s "on-demand throughput isn't supported" - and has the same rules as on the Claude API (effort xhigh, disabled at high or below, between_tools / enabled / prefill / temperature 400s), json_schema included. Walkthrough: `examples/anthropic/ClaudeHaiku55SmokeTest` (all 7 Claude API sections passed); prompts over 100k tokens cost more on Haiku 5.5 (the other 1M-context models bill long context at standard rates). **Access modes** (`examples/anthropic/AnthropicAccessModesSmokeTest [api|oauth|foundry|bedrock]`, the same chat / typed-stream tool call / JSON checks per mode, live 2026-10-07): API key (Haiku 5.5) and Microsoft Foundry (`customInstance` at `https://<resource>.services.ai.azure.com/anthropic/v1/` with `x-api-key`; the Foundry test resource deploys only `claude-sonnet-4-5`, json_schema works) pass; a Claude subscription OAuth token (`asOpenAIWithAuthToken`) serves ONLY Haiku 4.5 outside Claude Code - every other model answers a bare 429 `rate_limit_error` "Error" (classified as a rate limit, so retries are futile; it is Anthropic's gate on Claude Code's identity prompt - NOT to be worked around by the library). The OAuth mode was broken for EVERY model in 1.4.0: the default message betas carried `context-1m-2025-08-07`, retired 2026-04-30 (1M is the default wherever a model has it), and a subscription token refuses any request with it (400 "The long context beta is not yet available for this subscription.") - removed from `Anthropic.messageBetaHeaders`, pinned by `AnthropicStreamedHttpErrorsWireSpec`; every other default beta passes with both auth modes. Bedrock: the renewed test credentials (`AWS_BEDROCK_ACCESS_KEY` / `_SECRET_KEY`, `AWS_BEDROCK_BEARER_TOKEN` -> map to `AWS_BEARER_TOKEN_BEDROCK`) pass all three checks on Haiku 5.5 in both auth modes. Direct-API examples use `ThinkingSettings.adaptiveSummarized`: Sonnet 5 rejects `thinking.type = enabled` ("Use thinking.type.adaptive and output_config.effort"), and the claude-3-x / sonnet-4-20250514 ids are retired (404). Supports MCP toolsets, extended thinking, fast mode, tools (bash, code execution, computer use, web search/fetch, text editor, memory). Also supports x-api-key, static bearer/OAuth token (`ANTHROPIC_AUTH_TOKEN` → `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE` → `CLAUDE_CODE_OAUTH_TOKEN`; the `_ALTERNATIVE` var is safe to export persistently since the real `claude` CLI never reads it), and `ant auth` OAuth profiles with auto-refresh (`forAuthToken` / `forOAuthProfile` / `forAuthTokenProvider` / `customInstance`).
- **google-vertexai-client** (`google_vertexai_client`): Google Vertex AI client (Gemini models on GCP). Supports tools (function declarations, Google search, code execution) with ToolConfig.
- **google-gemini-client** (`google_gemini_client`): Google Gemini API client (direct Gemini API). Supports tools, prompt caching, thinking levels, and has its own exception hierarchy (GeminiScalaClientException) with error code handling.
- **perplexity-sonar-client** (`perplexity_sonar_client`): Perplexity client. `SonarService` carries the **Agent API** (`/v1/agent`, `/v1/models`; domain in `perplexity/domain/agent/`, JSON in `AgentJsonFormats` - request body built by `createAgentRequestBody`, lenient reads with `Unknown` passthrough for output items / stream events) and the retiring Sonar chat completions API (`createChatCompletion(Streamed)` + `asOpenAI`, `@deprecated` - Perplexity ends it 2026-09-27). Agent streams go through `execRawStream` + the module's own `service/impl/ServerSentEvents` decoder (LF and CRLF framing, multi-line data, `[DONE]`, a non-SSE body - e.g. a JSON error answering the request - is surfaced, never dropped), because the framing could not be observed live. Tests: `AgentOpenApiConformanceSpec` validates every written request against the vendored `perplexity-openapi.json` (fetched 2026-09-25; `prompt_cache_key` / `service_tier` are documented in the guides but missing from the spec) and decodes a generated minimal + maximal instance of every output item / stream event schema plus the documented response examples (`agent-docs-examples/`); `SonarAgentWireSpec` runs the real engine against a local HTTP server. Errors: `service/PerplexityScalaClientException.scala` (native hierarchy classified in `HandlePerplexityErrorCodes` by status + `error.type` - bodies `{"error":{"message","type","code"}}`, `code` = the HTTP status, so a raw stream's error body is classified by it; `x-request-id` rides along, `PerplexityRetryable`). Live facts (2026-09-25): stream framing is LF with `event:` lines and NO `[DONE]`; in a stream Perplexity's own `search_web` call shows up as a `function_call` output item (not in the final output); annotations are empty - citations are `[n]` markers in the text + `search_results`; a bare `model` works without `max_output_tokens` despite the spec; the per-response endpoints (cancel, files, a 404 retrieve) 429 after a short burst. OpenAI adapter: `SonarServiceFactory.agentAsOpenAI` = core `OpenAIResponsesChatCompletionService` over `impl/PerplexityResponsesServiceImpl` (Perplexity's `/v1/responses` alias of `/v1/agent`; create / retrieve / typed stream, the rest fails as unsupported; Perplexity errors repacked onto `OpenAIScala*` with the native one as the cause) - no dependency on openai-client; presets are NOT reachable through it (use `provider/model` ids); Perplexity answers `json_object` with `{}` so JSON needs `jsonSchemaModels = Seq(model)`; the adapter's plain `createChatCompletion` sends no Responses tools (web search goes via `createChatToolCompletion` / the stream). Core Responses reads were made tolerant for it (unknown output item types skipped, unknown `truncation` -> None). Live: `examples/sonar/PerplexityAgentAsOpenAISmokeTest`, `examples/sonar/PerplexityAgentApiSmokeTest` (needs `PERPLEXITY_API_KEY` or `SONAR_API_KEY`; ~$0.01 per run). Perplexity's Decisions API (the decision model `pplx-decider-v1-27b`) is NOT in this module: it takes the System One question format, so it lives in typesafe-client (`TypeSafeServiceFactory.perplexity`).
- **typesafe-client** (`typesafe_client`): TypeSafe AI System One API (`POST /v1/systemone`, `GET /v1/models`) - a decision model, not chat: `TypeSafeService.systemOne(state, questions)` sends named typed questions (`NoulQuestion` / `ChoiceQuestion` / `ScoreQuestion`) and returns typed `Answer`s with calibrated probabilities; unknown answer types arrive as `UnknownAnswer`. No streaming at all (live-verified 2026-09-17: `stream: true` is silently ignored and answered with plain JSON; there is no SSE endpoint - `/v1/systemone/stream` and `/sse` are 404). Limits (live-probed 2026-09-17, docs.typesafe.ai/models): ~32k input tokens (~150k chars) shared by state + questions (32,202 accepted, ~33.7k -> 400 `max_tokens_exceeded` -> `OpenAIScalaTokenCountExceededException`), $0.042 / 1M input tokens, output free, 250k tokens/s and 1,200 req/min. Token counts always come back (`usage.input_tokens` billable, `output_tokens` currently free, growing with the number of questions) and the OpenAI adapter maps them onto `UsageInfo(prompt_tokens, total_tokens, completion_tokens)`. `TypeSafeServiceFactory.asOpenAI()` is an `OpenAIChatCompletionService` for `json_schema` structured output ONLY (`impl/OpenAITypeSafeChatCompletionService` + `impl/SchemaQuestions`: boolean -> noul, string enum -> choice, numeric enum / `minimum`..`maximum` range of at most 10 values -> score (`ScoreQuestion.MaxLevels` - Jev 400 "Too many score levels. Must have at most 10 levels.", d1 422 and Perplexity 400 alike, live 2026-10-02 on jev-latest / jev-preview / jev-1.13.0 / d1:free / pplx-decider with text, object and array levels; documented on TypeSafe's score page - "takes up to 10", archived 2026-09-17 - but NOT in its OpenAPI spec, which has only `minItems: 1`, and by Perplexity as 1..10; 1.3.0 / 1.4.0 shipped an unverified cap of 32, so an 11-32-value range always failed at the API; the planner refuses a wider range or numeric enum by path, the domain an 11th level; levels sent as text - the API refuses numeric levels; integer -> most likely value, number -> expected value), array of string enum -> one noul per option, nested objects; messages -> state via the public `TypeSafeChatMapping.toState` (system/developer -> `instructions`, a JSON-object/array user message embedded as JSON, lone user message alone = the state, with instructions -> `{instructions, message}`, several turns -> `{instructions, conversation}`; chosen by `examples/typesafe/TypeSafeMessageMappingBenchmark`: instructions in the state score 8/8 at a flat ~110 tokens vs 7/8 and ~110 tokens PER question when copied into every question); `TypeSafeChatMapping.toQuestions(schema)` previews the questions (named by their paths joined with `.`, a `.` / `\` inside a property name or option backslash-escaped by `SchemaQuestions.questionName` - so `{"a.b": ...}` and `{"a": {"b": ...}}` never share an answer, issue #128; live 2026-10-01 both Jev and d1 keep a question key byte for byte - spaces, case, backslashes, dot-only keys, newlines, emoji, 1,000 chars - and refuse only an empty one (Jev 400 "Question key cannot be empty.", d1 422), so the planner refuses a top-level `""` property up front); `examples/typesafe/TypeSafeOpenAIAdapterScenarios` walks 16 live cases incl. every refusal; answers -> the assistant content as JSON, `SystemOneResponse` in `originalResponse`; noul threshold via `setTypeSafeNoulThreshold`; anything else refused before I/O). **Confidence fields** (`SchemaQuestions`): a `number` property `<base>_confidence` / `<base>Confidence` (case-sensitive suffix, exact sibling name - `isUrgentConfidence` does NOT match `is_urgent`) with a sibling `<base>` in the same object, at any depth, is not asked but filled from the answers, right after its base field, rounded half-up to 4 decimals: boolean -> the probability of the emitted answer (`noul` if it reads true at the threshold, else `1 - noul`), choice / score -> the answer's peakedness `confidence`, multi-select -> the minimum over its options (as booleans), object -> the minimum over every question underneath; both spellings may be declared; a confidence-like property without a sibling (or whose base is itself a confidence field) is planned as usual (and refused as a free number), a non-`number` one is refused; a question without a usable answer leaves the confidence out with a WARN. Works on the typed and the legacy map-form schema alike (the planner reads the schema JSON). Live-verified 2026-09-28 (`TypeSafeOpenAIAdapterScenarios` 7b). Of the standard settings only `model`, `response_format_type` (must be json_schema), `jsonSchema` and `n` = 1 are honoured - every other one that is set (temperature, top_p, stop, max_tokens, penalties, logit_bias, logprobs, top_logprobs, user, seed, store, reasoning_effort, verbosity, service_tier, parallel_tool_calls, metadata, foreign extra_params) is dropped with a single warning naming them (`OpenAITypeSafeChatCompletionService.unsupportedSettings` / `unsupportedSettingsMessage`). `jev-*` are in `models-supporting-json-schema` so `createChatCompletionWithJSON` stays in schema mode (exact-name or `-<name>` suffix match); an unlisted id (a new dated build `jev-1.14.0`, another host's id, or no openai-client - and so no config - on the classpath) gets the JSON helper's JSON-object fallback, whose schema the adapter reads back from the prompt and keeps out of the state (`OpenAIChatCompletionExtra.jsonSchemaFromPrompt`, next to the code that appends it; it keeps an empty / blank user message the schema was appended to - only a message added for the schema alone is dropped) - so listing is no longer required, but add each new dated build alongside its `NonOpenAIModelId` constant anyway. Env: `TYPESAFE_API_KEY`, optional `TYPESAFE_BASE_URL` / `TYPESAFE_DEFAULT_MODEL` (same as the official SDKs). Errors are a native hierarchy (`service/TypeSafeScalaClientException.scala`, classified by status + `detail.error_type` in `HandleTypeSafeErrorCodes.toException`, bodies collected live 2026-09-17: Unauthorized 401/403, TokenCountExceeded 400 max_tokens_exceeded, ApiUsage 400 api_usage_error, InvalidRequest 400 plain-detail / 422 with `violations`, NotFound 404/405, ClientTimeout 408 + transport, RateLimit 429, EngineOverloaded 503/529, ServerError 5xx; each carries httpCode / errorType / requestId via the shared `io.cequence.openaiscala.ProviderErrorDetails` trait, whose `unapply` walks the cause chain so repacked OpenAIScala* exceptions expose them too); `TypeSafeRetryable` classifies, `TypeSafeServiceAdapters.retry` uses it; the OpenAI adapter repacks them via `impl.repackAsOpenAIException` (native as cause). A 429 could not be provoked (800 requests in a burst all 200). Tests pin the wire format against the vendored OpenAPI spec (`src/test/resources/typesafe-openapi.json`, 0.2.0) and the Python SDK's fixtures, plus a local-HTTP-server wire spec. Live-verified 2026-09-16 (`examples/typesafe/TypeSafeSmokeTest`, 11 sections): `jev-latest` -> `jev-1.13.0` in the response, `jev-preview` exists, the dated `jev-1.12` is gone; a noul without instructions/criteria, an empty choice, a choice with more than 255 options (`ChoiceQuestion.MaxOptions`), a numeric score level and a non-text/object/array state are 400/422 on the server and `IllegalArgumentException`s here (the schema planner reports a >255 enum by path); an org-gated `bounding_box` question type exists but is unmodelled (it would arrive as `UnknownAnswer`). **Liquid AI's decision model d1** (launched 2026-09-30) serves the same System One API: `TypeSafeServiceFactory.liquid` / `liquidWithEngine` / `liquidAsOpenAI` (base `https://api.liquid.ai/decisions/` - at the host root `/v1/models` is the website, while `/decisions/v1/systemone` and `/decisions/v1/models` both work; `LIQUID_API_KEY`; `d1:free`, in `models-supporting-json-schema`). Live 2026-09-30: `/decisions/v1/models` lists only `d1:free`; d1 reports `output_tokens` 0; errors are OpenAI-style `{"error": {"message", "type", "code"}}` (launch day: a 5xx "All upstream backends failed for this model", then 429 `model_unavailable`) - classified by status, `errorType` falls back to `error.code` / `error.type`; `examples/typesafe/LiquidD1SmokeTest` runs d1 and Jev side by side, plus a latency benchmark (`nobench` skips it): median per call on a kept-alive connection, d1 356 / 346 / 559 / 853 / 1517 ms at 1 / 3 / 10 / 20 / 40 questions (~340 ms + ~30 ms per question beyond three - it seems to answer them one by one), Jev a flat 236-273 ms; a new connection per call adds ~90 ms to both; the answers agree (d1 0 output tokens, Jev 73). d1:free was intermittently unavailable on launch day (429 `model_unavailable` after a ~90-call burst, then again for ~10 minutes at one call every two minutes; right after a stretch, back-to-back calls took up to ~9 s) - the benchmark paces its calls 250 ms apart and the smoke test retries at most 3 times. Liquid's OpenAI-compatible chat surface (`ChatProviderSettings.liquid`, `https://api.liquid.ai/openai/v1/`) answers in OpenAI's format but lists no models for a free-tier key. **Perplexity's Decisions API** (`pplx-decider-v1.1-27b` since 2026-10-06 - open weights under Apache-2.0 on a Qwen3.8-27B backbone, Decision Index 61.56 vs 56.4, "250k context" per the launch post; `pplx-decider-v1-27b` launched 2026-10-01; live 2026-10-08 the two ids answer byte for byte alike with equal usage on nine questions over three states, so the API serves one model under both names; input $0.02 / 1M since the 2026-10-08 spec, was $0.04; the preset defaults to v1.1 and lists both) takes the same questions at `POST https://api.perplexity.ai/v1/decisions`: `TypeSafeServiceFactory.perplexity` / `perplexityWithEngine` / `perplexityAsOpenAI` (key `PERPLEXITY_API_KEY`, else `SONAR_API_KEY`; in `models-supporting-json-schema`); the host is the provider `DecisionProviderSettings.perplexity` (path `v1/decisions`, a `Fixed` model list, since Perplexity's `/v1/models` lists its Agent API models and not the deciders, `images = DecisionImages.InState`, 128 questions, `SONAR_API_KEY` as the key fallback, NO `maxImageTiles` since 2026-10-08 - see Images). Live facts 2026-10-02 (vendored spec `src/test/resources/perplexity-decisions-openapi.json`, 0.1.0, from `docs.perplexity.ai/openapi-gateway-preview.json`, pinned by `PerplexityDecisionsOpenApiConformanceSpec`): an unknown top-level field is a 400 (the client writes only model / state / questions); 1..128 questions (d1 also stops at 128, Jev took 129), 1..255 options, 1..10 score levels; an input of 262,144+ tokens is a 400 "Input length (262144) exceeds or equals model's maximum context length" -> `TypeSafeScalaTokenCountExceededException`; a body over 32 MiB a 413 -> InvalidRequest; 10 requests/s per organization (429 with `Retry-After`). Errors are `{"error": {"message", "type", "code"}}` with `code` a mere status ("400", 401, null), so `errorType` takes `error.type` (`invalid_request`, `invalid_request_error`, `invalid_api_key`, `too_many_requests`) - a non-numeric code (Liquid's `model_unavailable`) still wins; 404 (a trailing slash too) / 405 have empty bodies and a 504 may be an HTML page (both shortened in the message); `x-request-id` comes on 200 and most errors (not 401 / 404 / 504) and is read next to `x-typesafe-request-id`. Its validation messages are TypeSafe's word for word ("Noul question must have criteria or instructions", "Question key cannot be empty", "Too many score levels..."). **Images**: OpenAI-style `image_url` parts with base64 PNG / JPEG / WebP data URLs, read anywhere in the state (top-level array, the whole state, nested in an object - all scored a blue square 0.99 at ~160 tokens); an http(s) URL is a 400; SIZE: until 2026-10-06 an image over 2,048 tiles of 32 x 32 px (width and height rounded to the nearest 32: 1600 x 1310 = 2,050) was NOT refused but timed out (504) after ~1 minute - since then the API scales any image to ~2,100 input tokens (2026-10-08: 2,112 / 35,344 / 65,536 tiles cost 2,109 / 2,118 / 2,118 tokens, answered in 0.4-1.4 s, both ids), so the Perplexity preset has NO `maxImageTiles` and the public `domain/DecisionImage` builders (`DecisionImage(bytes)` - type from the magic bytes - / `fromDataUrl(url)`) refuse only a bad format or URL; the header-read size (PNG / JPEG (SOFn scan) / WebP (VP8 / VP8L / VP8X); sizes as `Long` - a malformed PNG may declare 2^31+; only the first 256 KB of base64 decoded unless a JPEG's SOFn lies further in) is checked before I/O against a provider's `maxImageTiles` where one is set (none of the presets; `DecisionImage.tiles` is its unit) - only then is the base64 decoded (2026-10-09; it used to decode every image for a size nobody compared: ~0.5 ms and ~700 KB of garbage per image, a 12 MiB JPEG with a late SOFn whole); `imageUrls` collects the URLs without rebuilding the state (`lift` rebuilds it, once, in the codec); both entry points check the same way - `systemOne` and, on the OpenAI protocol, `createDecision` (which skipped the checks until 2026-10-09). Host caps checked before sending: `DecisionProvider.maxQuestions` = 128 for Perplexity and Liquid (Jev took 129: none), `maxImageTiles` where set; a refusal by `systemOne` (IllegalArgumentException, thrown synchronously like the other request checks) surfaces from the adapter as an `OpenAIScalaClientException` (`repackAsOpenAIException` is applied to the whole chain). The request id prefers `x-typesafe-request-id` over `x-request-id` by priority (live: Jev sends only its own, d1 none). A missing Perplexity key names both `PERPLEXITY_API_KEY` and `SONAR_API_KEY`. Jev and `d1:free` read an image part as text (near-uniform answers, ~4x the tokens), so the adapter maps image content only with `imageInput = true` (`perplexityAsOpenAI`, `liquidAsOpenAI`, `asOpenAI(provider)` when `provider.readsImages`, or `asOpenAI(service, imageInput = true)` for a wrapped service; public `TypeSafeChatMapping.toState(messages, images)` - a user message with images becomes an array of its parts, consecutive text parts joined). `examples/typesafe/PerplexityDeciderSmokeTest` (shared `DecisionModelBenchmark`, also used by `LiquidD1SmokeTest`): the answers agree with Jev and d1; the decider takes ~210 ms at 1-3 questions plus ~7 ms per question (457 ms at 40) - the fastest of the three for a few questions; one run had ~1.1 s medians at 1-3 questions (a slow stretch); 2026-10-08 (v1.1, all sections passed): 282 / 264 / 369 / 528 / 862 ms at 1 / 3 / 10 / 20 / 40 (~15 ms per question), Jev 240-259 flat, d1:free 229-378 with 3 of 8 calls failing at 1-10 questions; the v1 id's answers to the docs' review changed with the update (defect 0.942 -> 0.996, mixed 0.950 -> 0.774, severity 1.78 -> 1.94) - one model under both names. **Decision providers** (like `ChatProviderSettings` for chat): `domain/DecisionProvider` (baseUrl, apiKeyEnvVariable, defaultModel, `decisionsPath` - `v1/systemone` or Perplexity's `v1/decisions` -, `models: DecisionModelListing` = `TypeSafe` (`GET v1/models` -> `{"models"}`) | `OpenAIStyle(query)` (`GET v1/models?<query>` -> `{"data": [{id, description, created}]}`, the release date from `created`) | `Fixed(models)`, `maxQuestions`, `images: DecisionImages` = `Unsupported` | `InState` | `ImagesField` (`readsImages`), `requestIdHeaders` by priority, `name` (label; the host name when unset), `apiKeyEnvFallbacks`, `maxImageTiles`, `protocol`, `apiKeyRequired`; `apiKeyFromEnv` names every variable when none is set, or is empty for a host that needs no key - then no `Authorization` header goes out) with the presets in `service/DecisionProviderSettings` (`typeSafe`, `liquid`, `perplexity`, `openAI`, `llamaCpp`, `openRouter`) - it replaced the private host profile; factory: `TypeSafeServiceFactory(provider)`, `forProvider(provider, apiKey: Option[String], timeouts)`, `withEngine(engine, provider[, apiKey])`, `asOpenAI(provider)` (image content per `provider.readsImages`); `liquid*` / `perplexity*` are shorthands. **OpenRouter** (`DecisionProviderSettings.openRouter`, `https://openrouter.ai/api/` + `v1/systemone`, `OPENROUTER_API_KEY`; live 2026-10-02, `examples/typesafe/OpenRouterDecisionsSmokeTest`): its decision models are listed ONLY with `GET /api/v1/models?output_modalities=decisions` (the plain list leaves them out) - `~typesafe/jev-latest` (default; bare `jev-latest` / `jev-1.13` work too), `typesafe/jev-1.13`, `liquid/d1`, `upstage/solar-decide`, `inception/mercury-decide:free`, `togethercomputer/tev1-4b-experimental`, `jaredpalmer/kev-4b`, `respan/span-01` / `-lite` / `-lite:free`, and, all live 2026-10-09 (16 decision models listed that day), `perplexity/pplx-decider-v1.1-27b` / `-v1-27b` (v1.1 answers as on Perplexity directly - defect 0.996 / mixed 0.77 / severity 1.94, 286 ms), `openai/gpt-6-luna-decisions` (1.000 / 1.00 / 1.78, 273 ms), `cloudflare/clef` / `clef-flash` (0.970 / 0.57 / 1.65 and 0.860 / 0.82 / 1.41), `upstage/solar-decide-flash` (0.977 / 0.99 / 1.12, ~0.9 s) and the paid `inception/mercury-decide` (constants `openrouter_*` in `NonOpenAIModelId` / `TypeSafeModelId`, all in `models-supporting-json-schema`); Span-01 judges with noul questions only (400 "Respan only accepts noul questions whose instructions and criteria are plain strings") over a text state or a conversation trace `{"input": [messages], "output": message}` (400 for any other object); the response `model` is the dated build (`liquid/d1-20260930`) plus extra `id` / `provider` / `usage.cost` fields; the request id is the `x-generation-id` header; errors `{"error": {"message", "code": <int>}}`, an upstream refusal wrapped as `HTTP 422: {...}` with the upstream status. Median latency (shared engine, ms, 1 / 10 / 40 questions): Jev 244 / 243 / 283, d1 358 / 336 / 460, Mercury Decide 517 / 642 / 966, Tev1 274 / 506 / 1,723, Kev 451 / 659 / 795, Solar Decide 985 / 10,633 / 46,878 (it slows ~1.2 s per question). Other hosts copy the protocol (Upstage, Vercel AI Gateway, meraGPT, milliseconds.ai, SiliconFlow, Berget, Opper, Featherless, Ollama 0.35+ locally - not live-verified, no keys); xAI's `POST /v1/decisions` exists but is gated (403 "Access to the decisions endpoint is denied" - an xAI key needs the permission in console.x.ai); OpenAI's went public on 2026-10-06 with a protocol of its own (the next bullet). **Microsoft-Decision-1** (Microsoft Foundry, public preview 2026-10-09; Qwen3.5-9B base, $0.042 / 1M input tokens, US / EU data zones): the launch post's example speaks System One - `POST <FOUNDRY_BASE_URL>/v1/systemone`, `Authorization: Bearer <FOUNDRY_API_KEY>`, `answers.<name>.choice` - so `DecisionProviderSettings.microsoftFoundry(baseUrl)` / `TypeSafeServiceFactory.microsoftFoundry()` (+ `WithEngine`, `AsOpenAI`; `NonOpenAIModelId.microsoft_decision_1`, in `models-supporting-json-schema`; request id from Azure's `apim-request-id`) serve it like Jev. NOT live-verified: it needs a deployment in a Foundry subscription (the catalog page is behind a sign-in; the Anthropic Foundry resource answers 404 `Resource not found` on `/v1/systemone`, `/models/v1/systemone`, `/openai/v1/systemone` and `/v1/decisions` with both header styles - probed 2026-10-09), the Learn docs do not list it yet, and the post says to confirm the route and header in the quickstart - pinned only by a `TypeSafeServiceWireSpec` case; `examples/typesafe/MicrosoftDecision1SmokeTest` is ready for a deployment (`FOUNDRY_BASE_URL`, `FOUNDRY_API_KEY`, optional `FOUNDRY_MODEL`).
- **OpenAI's Decisions API as a decision provider** (typesafe-client, 2026-10-07): `DecisionProviderSettings.openAI`
  (`gpt-6-luna`, `OPENAI_SCALA_CLIENT_API_KEY` else `OPENAI_API_KEY`, `v1/decisions`, a fixed model list, 200 questions,
  images, `x-request-id`) speaks `DecisionProtocol.OpenAI` - `DecisionProvider.protocol` picks the wire format and
  `TypeSafeServiceImpl` the `impl/DecisionCodec` (`SystemOneCodec` | `OpenAIDecisionsCodec`, which reuses core's
  `domain/decisions` JSON): a noul -> a predicate (yes / no criteria appended to the instructions), a choice -> a choice
  (criteria as descriptions), a score -> a score (`{label, description}` levels kept); the state -> `input` (text as is,
  JSON as its compact text with each image part replaced by `[image n]` and sent as an `input_image`, a chat message's
  parts as one message); answers back by name (by position when unnamed), a refusal -> `UnknownAnswer("refusal", ...)`.
  "Decision input exceeds the token limit." is a `TypeSafeScalaTokenCountExceededException` (rerank splits on it).
  `DecisionProvider.maxImageTiles` (none for OpenAI, which scales images - nor, since 2026-10-08, for Perplexity, which scales them too; a host that needs one sets it) replaced the tile cap
  that image support implied; the chat mapping (`TypeSafeChatMapping.toState`) now checks an image's format only.
  Live 2026-10-07 (`examples/typesafe/OpenAIDecisionsSmokeTest`, all passed): native createDecision (refund 1.0, billing
  1.0, urgency 1.79), a refusal, `decide[Triage]` (Billing), rerank (reset passage 1.00, injection 0.00), an image (blue
  1.0), `createChatCompletionWithJSON` through `asOpenAI(DecisionProviderSettings.openAI)` and a guardrail (injection
  flagged), 230-360 ms each when warm. Pinned by `DecisionCodecSpec` and three cases in `TypeSafeServiceWireSpec`.
- **Liquid's d1 with images, Open d1 and llama.cpp** (typesafe-client, 2026-10-07):
  - Liquid lists the paid `d1` (`TypeSafeModelId.liquid_d1`, text + image, $0.04 / 1M input tokens, `usage.cost` in the
    response) next to `d1:free` (text only: an image is a 422 "The model `d1:free` does not accept images."). Its
    `/decisions/v1/models` entries carry `input_modalities` -> `ModelMetadata.input_modalities` (also read from an
    OpenAI-style list's `architecture.input_modalities`).
  - Images: `DecisionImages.ImagesField` (Liquid, llama.cpp) - `DecisionCodec(provider)` lifts the state's image parts
    into a top-level `images` array and leaves `[image n]` markers (`DecisionImage.lift`, shared with the OpenAI codec).
    Live: Liquid lifts the parts of an ARRAY state itself, but reads one nested in an object or a lone-part state as
    text (blue 0.56, 227 tokens vs 0.9999 lifted); at most 8 images, 10,000 32 x 32 patches in all, aspect ratio 100
    (each a quick 422); PNG / JPEG / WebP only - GIF is a 422 despite the docs (Perplexity refuses GIF too; OpenAI's
    Decisions API takes it, but the client's format check still refuses it); `audio` is silently ignored.
  - Open d1 (d1-3B: LFM2.5-VL-3B, 32k context; d1-omni-600M: text + images or 16 kHz audio up to 30 s, experimental;
    GGUF repos `LiquidAI/d1-3B-GGUF`, `LiquidAI/d1-omni-600M-GGUF`, served by `llama-server` at `/v1/systemone` with
    `images` / `audio` fields): mainline llama.cpp b11476 (2026-10-07) could NOT load either ("unsupported decision
    model type: d1"; the omni GGUF fails on a missing tensor) and no llama.cpp PR adds it yet - re-check before claiming
    support. Constants `liquid_d1_3b_gguf` / `liquid_d1_omni_600m_gguf` = the router-mode ids (`LiquidAI/d1-3B-GGUF:Q8_0`).
  - `DecisionProviderSettings.llamaCpp` (`http://127.0.0.1:8080/`, key optional `LLAMA_API_KEY`, `OpenAIStyle()` listing
    keeping only entries whose `architecture.output_modalities` contain `decisions` (or have none), `ImagesField`, no
    request id header). Live with b11476 and Julia-1 / Laya (text-only decision models llama.cpp does run): a server of
    one model ignores `model` (answers with the file path); a router needs it (400 "model name is missing from the
    request" / "model 'x' not found"); `?output_modalities=` is ignored; an image part in a plain array state is read as
    TEXT (llama.cpp lifts only chat-message parts - hence `ImagesField`); `images` on a model without a projector is a
    501 `not_supported_error` -> `TypeSafeScalaInvalidRequestException` (501 is not retried); `stream` / unknown fields
    ignored; a null state is a 400; score answers carry a `legend`. Julia-1 (168 MB) put the typed triage in Sales;
    Laya got it right.
  - Live examples: `examples/typesafe/LiquidD1ImagesSmokeTest` (nested image, two images by order, the OpenAI adapter
    with an image, `d1:free` refused - all passed, 420-730 ms) and `LlamaCppDecisionsSmokeTest [model]` (all passed with
    `Laya-Q8_0`). Pinned by `DecisionCodecSpec`, `DecisionImageSpec` (`lift`) and four cases in `TypeSafeServiceWireSpec`.
- **Typed decisions** (typesafe-client, 2026-10-04): `decide[T: JsonSchemaOf: Reads](state: JsValue | String,
  model, noulThreshold)` in `service/DecisionServiceExtra` (an implicit class over any `TypeSafeService`, like
  `OpenAIChatCompletionExtra`; the user's call: routines live above the decision service, never on it, nor on the
  chat services). The schema comes from core's
  `JsonSchemaOf`, is planned by `SchemaQuestions.plan` (a free text field is refused before I/O with an
  `IllegalArgumentException`), asked via `systemOne`, assembled by `SchemaQuestions.assemble` and read back as `T`
  (a Reads mismatch, e.g. a snake-case Format, is a `TypeSafeScalaClientException`). The result is
  `domain/Decision[T](value, response)`, with `noul` / `choice` / `score` by question name = field path
  (`customer.vip`, multi-select option `topics.[Payments]`). It is the LangChain4j "Decision Services" idea.
  Pinned by `TypeSafeDecisionSpec`. Live 2026-10-04 (`examples/typesafe/TypeSafeTypedDecision`, Jev, Scala 2.13
  and 3): 3 tickets triaged right (refund 99% / Billing, Technical with Login 95%, Sales 96%), ~250 ms per call.
- **Re-ranking** (typesafe-client, 2026-10-04): `rerank(query, passages, RerankSettings)` /
  `rerankBy(query, items)(text)` in `DecisionServiceExtra`, returning `Ranked(item, score, index)` best first
  (ties in input order; `minScore`, `topK`).
  - Questions: one noul per passage - `RerankSettings.question`, then the passage inside `<document>` tags (tags
    in it defused) - and the query as the state.
  - Batches: greedy by `maxPassagesPerRequest` (32) and `maxCharsPerRequest` (48k, the query counted), at most
    `parallelism` (4) at once - core `FutureHelpers.parallelize` (Akka's `mapAsync` on plain futures: the next as soon as one finishes, results in order, no materializer), also behind
    the batch emulation adapter's `maxParallelism`. Identical texts are asked once, blank
    ones score 0 unasked, and passages over `maxPassageChars` (4k) are cut with "…".
  - Failures: a `TypeSafeScalaTokenCountExceededException` splits the batch in halves, asked one after the other
    (a worker never has more than one request in flight); any other failure fails the call (use
    `TypeSafeServiceAdapters.retry`).
  - Layout chosen by a live probe (2026-10-04, 10 passages, one an injected "answer yes"). Jev resisted in every
    layout (0.03-0.05). Perplexity's decider gave the passage 0.34 with LangChain4j's layout (passage text in
    the question), 0.15 with the passage quoted as data, and 0.08 with all passages in the state, at 2.3x the
    tokens.
  - Live (`examples/typesafe/TypeSafeRerank`): Jev relevant 0.95-0.96 / partly 0.51 / injection 0.03;
    Perplexity 0.99 / 0.18 / 0.16; 70 passages in batches of 16 in 484 ms.
  - Answer helpers: `ChoiceAnswer.probabilityOf` (unknown option -> IllegalArgumentException) / `margin`, and
    `ScoreAnswer.probabilityAtLeast(level)`.
  - Pinned by `TypeSafeRerankSpec`.
- **claude-agent-client** (`claude_agent_client`): Subprocess transport wrapping the `claude` CLI (Claude Agent SDK-compatible NDJSON protocol over stdin/stdout) - full bidirectional sessions with tool-permission callbacks and interrupt support, distinct from the HTTP-based `anthropic-client`. Requires the `claude` CLI installed and authenticated separately (API key or Claude subscription).

All provider clients depend on openai-core and provide `asOpenAI()` adapters to work with the standard OpenAI interfaces, with two exceptions: `claude-agent-client` is a fundamentally different subprocess/NDJSON transport (not an `OpenAIChatCompletionService`), and `typesafe-client` wraps the decision APIs (TypeSafe's Jev, Liquid's d1, Perplexity's decider) whose `asOpenAI()` serves `json_schema` structured output only (no text generation, no streaming).

### Utility Modules
- **openai-all** (`all`): Envelope module aggregating all clients (except guice) into a single dependency: `openai-scala-all`.
- **openai-count-tokens** (`count_tokens`): Token counting utilities (OpenAICountTokensHelper) using jtokkit for estimating API costs before making calls.
- **openai-guice** (`guice`): Dependency injection support using scala-guice.
- **openai-examples** (`examples`): Comprehensive examples demonstrating all features.

### Module Dependency Graph
```
openai-core
    ├── openai-client (aggregates core)
    │   ├── openai-client-stream (aggregates client)
    │   └── openai-count-tokens
    ├── anthropic-client (aggregates core + client + client-stream)
    │   └── claude-agent-client (subprocess transport; depends on core + anthropic-client)
    ├── google-vertexai-client (aggregates core + client + client-stream)
    ├── google-gemini-client (aggregates core + client + client-stream)
    ├── perplexity-sonar-client (aggregates core + client + client-stream)
    └── typesafe-client (core only - plain request/response, no streaming)

openai-all depends on all streaming + provider clients + count-tokens
openai-guice depends on openai-client, aggregates count-tokens + all
openai-examples depends on all streaming + provider clients
```

## Key Architecture Patterns

### Service Hierarchy
The project uses a trait-based service hierarchy:
- **OpenAICoreService**: Minimal interface (listModels, createCompletion, createChatCompletion, createEmbeddings)
- **OpenAIChatCompletionService**: Chat completion specific
- **OpenAIService**: Full API including assistants, threads, files, batches, audio, images, responses API, etc.

All services extend `CloseableService` to ensure proper resource cleanup.

### Adapter Pattern
OpenAIServiceAdapters (in openai-core) provides composable adapters via factory methods:
- `OpenAIServiceAdapters.forFullService` / `.forChatCompletionService` / `.forCoreService`

Available adapters:
- **Load distribution**: `roundRobin()`, `randomOrder()`
- **Retry logic**: `retry()` with RetrySettings (supports `includeExceptionMessage` and `jitterMs`)
- **Logging**: `log()` for call monitoring
- **Pre-action**: `preAction()` for executing actions before each call
- **Routing**: `chatCompletionRouter()` for model-based routing across providers, `chatCompletionRouterMapped()` for model name transformation
- **Transformation**: `chatToCompletion()`, `chatCompletionInput()`, `chatCompletionOutput()`
- **Interception**: `chatCompletionIntercept()` for request/response interception with timing data
- **Error interception**: `chatCompletionErrorIntercept()` for capturing failed requests with error details and timing
- **Guardrails**: `guardrails(input, output, onViolation, outputReprompts, onVerdict)` (`service/adapter/GuardrailsAdapter`)
  - What it guards: only `createChatCompletion` / `createChatToolCompletion` (batches pass through; streams need
    `guardrailsWithStreaming`, below).
  - Order: the input guardrails run concurrently BEFORE the call (a block means no call); the output guardrails check
    every non-empty reply (a tool-call-only reply has no text).
  - On a block: `Reject` (default) fails with `OpenAIScalaGuardrailException(verdicts)` (not `Retryable`); `Respond`
    answers with a message, finish reason `content_filter`, and a `GuardrailBlock` in `originalResponse` - with
    several choices (`n` > 1) only the flagged ones are answered with it, the clean ones kept (OpenAI's own content
    filter is per choice too).
  - `outputReprompts`: appends the flagged reply plus a note, then asks again; usage is summed. Not done when a
    guard was `unavailable`, nor for a call carrying approval decisions - a resume: its decisions are one-shot and
    the resume backends ignore the appended messages (`GuardrailsAdapter.repromptsFor`).
  - Failures: a guardrail future that fails fails the call; a throwing `onVerdict` is ignored.
  - Guardrails are the `service/guardrails` traits `InputGuardrail` / `OutputGuardrail`, or `ModelGuardrail`
    (`ModelGuardrailSettings`, domain in `domain/guardrails/`).
  - `ModelGuardrail` asks all of a stage's `GuardrailCheck`s in ONE call to any `json_schema`-capable
    `OpenAIChatCompletionService`. That is an LLM, or a decision model via `TypeSafeServiceFactory.asOpenAI` (a
    noul per check). A decision model needs no `enforceJsonSchemaMode`: an id not in `models-supporting-json-schema`
    gets the JSON-object fallback, which its adapter reads the schema back from. Its noul threshold
    (`setTypeSafeNoulThreshold`) decides only the checks without a `threshold` of their own.
  - The schema `guardrail_verdict` has a boolean per check, whose question is "Does the user's message /
    assistant's reply <description>?" (a description ending in `?` is asked as written). A check with a `threshold`
    adds `<name>_confidence`: the TypeSafe adapter fills it from the probability (not asked), an LLM states it; then
    P(violation) >= threshold decides.
  - Prompting: only user text goes to the guard, in `<USER_MESSAGE>` (`[image]` / `[file]` markers); output checks
    send `<ASSISTANT_REPLY>` plus the last user message as context. A tag inside the text (any case, stray spaces,
    attributes, self-closing) is defused to `[...]` - `service/QuotedText`, shared with re-ranking.
    System messages and tool calls / results are never sent.
  - Behaviour: default scope `NewUserMessages` (trailing user messages, so a tool loop's later turns check nothing).
    Fails CLOSED by default (`GuardrailVerdict.UnavailableCheck`). `parseRetries` re-asks a non-verdict answer;
    transient errors are left to a retry adapter around the guard. Default checks are
    `GuardrailCheck.inputDefaults` / `outputDefaults` (`personalData` is opt-in).
  - Prompts: the guard's system prompts are templates (`ModelGuardrailSettings.prompts = GuardrailPrompts(input,
    output)`) with `{{policy}}` (the policy, or "N/A") and `{{checks}}` ("- name: question" lines). The defaults
    (`GuardrailPrompts.DefaultInput` / `DefaultOutput`) end with "Additional policy (N/A if none):". A policy whose
    prompt has no `{{policy}}` is refused at construction. The tagged user message is not configurable.
  - Streams: `OpenAIServiceAdapters.guardrailsWithStreaming(input, output, onViolation, outputReprompts,
    onVerdict)(streamedService)` (`service/adapter/GuardrailsStreamedAdapter`, in core). It guards the sync calls
    via `GuardrailsAdapter`, plus both streams: the OpenAI-shaped `createChatCompletionStreamed` and the typed 4-arg
    `createChatToolCompletionStreamed` (the final typed / approvals variants ride on it). Both adapters share
    `GuardrailRunner`, whose `checkOutput` returns an `OutputDecision` (`Pass` / `AskAgain` / `Block`); the sync
    adapter writes a block through the `GuardedReplies` type class (chat and tool responses), the streamed one
    through `StreamChunks`.
    - Order: the input checks run before the provider stream is even created (`Source.futureSource`).
    - The output guard is called ONCE, when the stream finishes (the user's call, 2026-10-03 - a segmented mode
      that checked the text so far per piece was built and removed): the reply is folded, checked, then
      released. Reprompts work and usage is summed into the usage chunks (one is added when the released attempt
      reports none). Without output guardrails the stream passes through untouched.
    - A `ChatChunk.Retry` voids what precedes it: only the chunks from the last restart are checked and released.
    - A block: `Reject` fails the stream with nothing of the reply released. `Respond` emits `Start` (the
      stream's own id / model when it had begun), the message, `Other("guardrail_block", verdicts JSON)` (typed
      only), `Finish(content_filter)` and the usage seen. An OpenAI-shaped stream with several choices keeps its
      clean ones as they came and answers each flagged one with the message (`StreamChunks.blockedReplies`).
    - Pinned by `GuardrailsStreamedAdapterSpec` (core) and a case in `StreamedWrappersDelegationSpec`.
  - Live-verified 2026-10-02 (`examples/guardrails/GuardrailsSmokeTest`, Scala 2.13 and 3): all 12 checks right with
    gpt-5.4-mini (no reasoning, ~0.9 s) and Jev (~250 ms, probabilities via thresholds). Jev also flags
    `spam_or_abuse` on an injection or a malware request; the blunt-but-fine message scores hate 0.27. Streams
    (2026-10-03, gpt-5.4-mini + Jev):
    - A clean question: the reply released whole once the stream finished and passed.
    - An injection: answered with the block message in ~250 ms, no provider call.
    - A reply sharing a discount code (an extra `discount_code` check): withheld; the code never reached the
      consumer.

Adapters are composable and can be stacked arbitrarily.

### Factory Pattern
Service creation is centralized through factories:
- `OpenAIServiceFactory()` - Full OpenAI service
- `OpenAICoreServiceFactory()` - Minimal service (compatible with FastChat, Ollama)
- `OpenAIChatCompletionServiceFactory()` - Chat-only service
- Provider-specific: `AnthropicServiceFactory.asOpenAI()`, `VertexAIServiceFactory.asOpenAI()`, `GeminiServiceFactory`, `SonarServiceFactory`

Factories support multiple initialization modes: default config, custom config, direct API key, Azure variants.

### Streaming Support
Streaming is provided as an extension via the `openai-client-stream` module:
- Import `OpenAIStreamedServiceImplicits._` to add `.withStreaming()` to factories
- `createChatCompletionStreamed` returns `Source[ChatCompletionChunkResponse, NotUsed]` (OpenAI-shaped chunks)
- **Typed streaming** (1.3.0): `createChatToolCompletionStreamed(messages, tools, responseToolChoice, settings)` /
  `createChatCompletionStreamedTyped` return `Source[ChatChunk, NotUsed]` - a provider-neutral sealed ADT
  (`domain/response/ChatChunk.scala`: `Start`, `Text`, `Thinking`, `ThinkingSignature`, `RedactedThinking`,
  `ToolCallStart`/`ToolCallDelta`/`ToolCall`, `ToolResult`, `Citation`, `Finish`, `Usage`, `Other`, plus two
  control events no provider mapper emits - `Retry(attempt, model)` inserted by whoever restarts a stream, which resets
  the assembler, and `Done`, an explicit terminator for transports that cannot signal completion). The trait
  default derives it from the OpenAI chunks via `service/ChatChunks.fromOpenAIChunks` (handles `delta.reasoning_content`
  / `delta.reasoning`; a turn that streamed tool calls finishes as `tool_calls` even when the provider says `stop`, as
  OpenAI does for a forced `tool_choice` - the raw value stays in `providerReason`); Anthropic (`impl/package.scala#toChatChunks`, requests `display = summarized` thinking) and
  Gemini (`OpenAIGeminiChatCompletionService.toChatChunks`, turns `includeThoughts` on) and Vertex AI
  (`vertexai/service/impl/VertexAIChatChunks`, protobuf parts; `setVertexAIIncludeThoughts`) override it natively and accept
  provider tools via `setAnthropicTools` / `setGeminiTools` / `setVertexAITools`. Anthropic's MCP connector rides on
  `setAnthropicMcpServers` (all adapter paths send `mcp_servers`; Anthropic API only, Bedrock rejects it) and the adapter
  continues `pause_turn` stops itself (`OpenAIAnthropicChatCompletionService.streamWithContinuation` /
  `createMessageWithContinuation`: echo the assistant blocks rebuilt by `StreamedChunkMapper` + "Continue from where you
  left off."; one Start, intermediate Finish/Usage dropped, final Usage summed, tool ordinals continue; capped by
  `setAnthropicMaxContinuations`, default 6). Gemini's native MCP (`setGeminiTools(Tool.McpServers)`) is server-executed:
  `OpenAIGeminiChatCompletionService.chatChunksFlow(McpCallRule)` labels MCP calls (`<server>_<tool>`, or any call that is
  not a declared client function tool - Gemini sometimes drops the prefix) `serverSide = true`, maps
  the `functionResponse` to `ToolResult`, holds the intermediate STOP of the call chunk, and reports a call the stream never
  answered as `ToolResult(isError = true)` (the plain path throws `GeminiScalaMcpCallNotExecutedException`, a
  `GeminiScalaServerErrorException` subtype -> `OpenAIScalaServerErrorException`, i.e. `Retryable`); `createChatToolCompletion` honours `setGeminiTools` too.
  Live-verified 2026-09-16: Authorization-bearer transport headers and multi-server requests work; failures are transient
  Gemini-side (500/503/dangling call), Gemini 3 echoes no call/result parts, Gemini 2.5 does. Grok, Groq, Cerebras, Fireworks and
  DeepSeek use the generic mapping (live-verified 2026-09-10; repeated per-chunk `usage` is deduplicated). Every streamed wrapper in `openai-client-stream` must
  delegate the 4-arg method explicitly (see `StreamedWrappersDelegationSpec`). `source.texts` is the legacy string view,
  `source.assembled` folds into `AssembledChatCompletion`. Two layers: the tool layer (`ToolCall*`/`ToolResult`, client and
  server tools alike) plus a semantic layer emitted in addition (`CodeExecution`/`CodeExecutionResult`, `WebSearch`/
  `WebSearchResult`, `Image`, `Refusal`, `Citation`); anything unmapped is `Other(kind, raw)`, never dropped.
- **Human approval mid-stream** (typed stream; live-verified 2026-09-29): a run paused until a tool call is
  approved emits one `ChatChunk.ToolApprovalRequest(requestId, toolName, arguments, serverName, runId, raw)` per pending
  call, then `Finish(FinishReason.approval_required)`, and ends (`AssembledChatCompletion.toolApprovalRequests` /
  `awaitingApproval` / `approveAll` / `denyAll`). Resume with a second call carrying the decisions
  (`request.approve` / `request.deny(reason)` -> `ToolApprovalDecision`, reason only on deny; one-shot - never on
  settings reused for later turns) via `ToolApprovalSettingsOps.setToolApprovalDecisions` (an `extra_params` key;
  stripped from the OpenAI chat body, never sent). A resume must reach the SAME OpenAI project / Anthropic workspace
  (no round-robin / random-order / parallel-take-first adapters across several; avoid retry adapters around it).
  Two backends:
  - **OpenAI** (Responses API, `MCPServerTool(requireApproval = true)` or a raw `MCPTool` via `setResponsesTools` -
    its `requireApproval = None` means the API default `always`): the pause is a normal `completed` response whose
    last item is `mcp_approval_request` (arguments already on `output_item.added`; no dedicated stream event),
    mapped on `output_item.done`, NOT on the tool layer (the approved call arrives as an `mcp_call` in the resumed
    stream). STATEFUL resume: any call whose tools may ask (or carrying decisions) defaults to `store = true`; the
    resume continues the paused response by id (`previous_response_id` = the requests' `runId`) sending only the
    system messages (as instructions), the trailing tool messages that answer the PAUSED response's own function
    calls (looked up with `getModelResponse(runId)` whenever tool messages trail - outputs the paused call already
    had, e.g. a tool-loop turn or an earlier resume, are not re-sent: live 2026-09-29, OpenAI silently ACCEPTS a
    re-sent `function_call_output`, duplicating it in the conversation), the answers and the tools again - so reasoning
    items and executed `mcp_call`s survive and a run may pause again (live: two pauses with reasoning effort medium).
    `store` defaults to true only when a tool may ask (`mayAskForApproval`: an unset / `always` raw `MCPTool`, or a
    filter whose `never` names do not cover every allowed tool) - Zero-Data-Retention orgs must pass `store = false`.
    An explicit `store = false` falls back to the stateless replay (the answered requests + answers after the
    history; an unanswered one is dropped) - one pause deep only (warned). Live facts: `store=false` +
    `previous_response_id` -> 404; a reason with `approve = true` -> 400; a pending request left unanswered -> 400.
    Decisions and `setResponsesTools` route to the Responses adapter (`chatToolsPreferResponsesAPI`) on
    `createChatToolCompletion(Streamed)` and `createChatCompletion` of the full service (the Responses view's
    `createChatCompletion` sends them; a paused run reports `approval_required` there too); every entry point that
    cannot carry Responses-native tools refuses them (`ResponsesChatCompletionSettingsOps.unsupportedResponsesSettings`,
    with a backstop in the chat body maker) - chat-only services refuse both. Sync `createChatToolCompletion` reports
    `finish_reason = "approval_required"` + `response.toolApprovalRequests`; the OpenAI-shaped
    `createChatCompletionStreamed` view reports the finish reason but cannot carry the requests. DeepWiki's tools are
    `read_wiki_structure` / `read_wiki_contents` / `ask_wiki_question` now (was `ask_question`).
  - **Anthropic Managed Agents** (`managedAgentAsOpenAI`, a native typed-stream override): tools with an `always_ask` /
    `auto` permission policy (configured on the agent: `agentTools` / a fixed agent - per-call tools are refused)
    pause the session: `agent.tool_use` / `agent.mcp_tool_use` with `evaluated_permission = ask` + a `session.status_idle`
    `requires_action {event_ids}`. The resume posts `user.tool_confirmation {tool_use_id: <event id>, result,
    deny_message (deny only)}` to the same session (`runId`; messages ignored), reports the answered calls as
    `ToolCall(serverSide)` first, then `agent.tool_result` (linked by `tool_use_id`, a denial is `is_error`) as
    `ToolResult`. Session lifecycle: deleted once a turn finishes, fails or is cancelled (BEFORE the stream completes,
    so `close()` right after cannot race it, and only once the POST's outcome is known); KEPT while paused (never
    auto-deleted - `deleteSession(runId)` to abandon) and when a resume can be retried with the same decisions - its
    confirmations were not applied (the POST failed) or its next pause could not be looked up (I/O); an unanswerable
    pause (`agent.custom_tool_use`, an id missing from the history: `UnanswerablePauseException`) fails and is
    deleted. A pending id not seen in the stream (partial resume) is looked up via `listSessionEvents`. A
    `session.error` with `retry_status` retrying is `Other` (the turn goes on); `exhausted` (the SDK: "this turn is
    dead", then a `retries_exhausted` idle) and `terminal` fail classified (overloaded / rate-limited) on every path,
    the sync one included. An event stream that closes before a terminal event fails the call
    (`OpenAIScalaServerErrorException`) instead of ending the turn quietly. The sync / OpenAI-chunk paths still fail
    on `requires_action`. `PermissionPolicy.auto`
    added. The session applies the confirmations of ONE POST one at a time and idles in between (`requires_action`
    listing the still-queued ids - live 2026-09-29, the queued one IS applied without another POST): such an interim
    idle (all ids confirmed by this resume) is passed through as `Other("session.status_idle")`, not a pause, and a
    real pause never re-asks a confirmed id. Known gap (TODO): the confirmation POST is not gated on the SSE
    subscription being live.
  - Everything else refuses rather than silently misbehaving: adapters / entry points that cannot resume refuse a call
    carrying decisions (`ToolApprovalSettingsOps.refusingDecisions`: Anthropic Messages / Bedrock, Gemini, Vertex AI,
    Sonar, TypeSafe, the OpenAI chat-only services, `createChatFunCompletion`, `createChatWebSearchCompletion`,
    `createChatCompletionBatch`, `ChatToCompletionAdapter`); providers that cannot pause refuse
    `MCPServerTool(requireApproval = true)` instead of running it unapproved (Anthropic's MCP connector, Gemini,
    Perplexity `agentAsOpenAI` - its backend is marked `ResponsesToolApprovalsUnsupported` and also refuses a raw
    `MCPTool` whose EXPLICIT `requireApproval` may ask; unset = the backend's default, auto-run).
    Pinned by `ToolApprovalWireSpec` (client-stream, replays the live-recorded SSE in `test/resources/tool-approval/`
    incl. a two-pause stateful run), `ManagedAgentToolApprovalWireSpec` (anthropic, a scripted session API that only
    delivers to live subscribers), `ToolApprovalSpec` (core) and the per-provider refusal tests; live demo
    `examples/CreateChatToolCompletionStreamedWithApproval` (`deny` arg to deny).
  - **Callback helper** (live-verified 2026-09-29 on both backends): the final
    `createChatToolCompletionStreamedWithApprovals(messages, tools, toolChoice, settings, maxApprovalRounds = 10)(decide:
    ToolApprovalRequest => Future[ToolApprovalDecision])` on `OpenAIChatCompletionStreamedServiceExtra` (so every
    streamed service) = core `service/ToolApprovalLoop`: asks `decide` about each pending request (sequentially, in
    order; a decision must answer its request) and resumes with `settings.setToolApprovalDecisions`, joining the rounds
    into ONE stream - the first round's `Start` only, the answered requests dropped (the callback saw them), every
    round's `Finish` / `Usage` / `Done` held back (the stream ends with the last `Finish`, the usage summed via
    `UsageInfo.sum`, `Done`), tool-call ordinals shifted to continue. Ends PAUSED (requests + `Finish(approval_required)` passed through, like the plain
    stream) after `maxApprovalRounds` resumes or when a paused round also has client function calls (no local tool
    execution). Per-round state is per materialization (`Source.lazySource`); the next round starts only after an
    end-of-round marker is processed (a failed / cancelled round never asks), via `Source.futureSource` (eager concat is
    harmless). A `Retry` passes in round 0 (resets the round) but fails a resumed round. Tests: `ToolApprovalLoopSpec`
    (core, fake provider), plus helper cases in both wire specs; live demo
    `examples/CreateChatToolCompletionStreamedWithApprovalCallback` (`ask` = console prompt, `deny`).
- **Provider-neutral tools** (1.3.0): `ChatCompletionTool.MCPServerTool` / `ChatCompletionTool.SkillTool` (openai-core
  `domain/AssistantTool.scala`) go in `tools` next to `FunctionTool`. OpenAI: `ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(model, tools)`
  routes any request carrying them through the Responses API (`OpenAIResponsesChatCompletionService.toResponsesTools`: `mcp`
  tool, skills as ONE hosted `ShellTool(ContainerAuto(skills))` - `responsesapi/tools/ShellTool.scala`, `shell_call` items map to
  server-side chunks); a chat-only service fails fast (`ChatCompletionBodyMaker.responsesOnlyToolsMessage`). Anthropic
  (`toAnthropicToolRequest`): `mcp_servers` (custom headers refused) and `container.skills` + code execution tool (skill beta
  headers now also sent on streams). Gemini (`toGeminiMcpServersTool`): one `mcpServers` tool, bearer -> Authorization header,
  allowedTools warned; SkillTool refused. Vertex AI: both refused (`rejectUnsupportedTools`).
- **Responses API streaming** (1.3.0): `OpenAIStreamedServiceExtra.createModelResponseStreamed(inputs, settings)` returns
  `Source[ResponseStreamEvent, NotUsed]` (`domain/responsesapi/ResponseStreamEvent.scala`, parsed on the JSON `type`;
  unknown events -> `UnknownEvent`) and `createModelResponseStreamedTyped` maps it via `ChatChunks.fromResponseEvents`.
  `OpenAIResponsesChatCompletionService` (the Responses-backed chat adapter) implements the streamed trait on top of it,
  and the merged `withStreaming` full service routes GPT-6 typed tool streams through it (`chatToolsRequireResponsesAPI`
  in `ChatCompletionSettingsConversions`).
- Streamed frames (one SSE event / JSON line) share ONE cap, `StreamingConsts.maxFrameLength`: 32 MiB unless set via
  `openai-scala-client.streaming.maxFrameLength` (a HOCON size, e.g. `128 MiB`; env
  `OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH`; read once). (64 MiB from 2026-10-06 to 2026-10-09, then 32: the cap is a ceiling, not an allocation, but it bounds the worst cases - ~2-3x the frame buffered per stream before parsing, a delimiter-less broken stream buffering the whole cap before failing, a dispatcher thread stalled parsing it - and Jackson refuses a single string over 20M chars anyway, so only multi-value frames above ~20 MiB need more; `StreamingConsts.DefaultMaxFrameLength`'s scaladoc says so) 1.4.0 hard-coded 1 MiB, which a frame carrying a generated
  image in base64 (Responses API image generation, Gemini image models up to 4K) or a fetched document exceeds;
  ws-client's own default is 20 KB. `ClassifiedStreamingWSClient.execJsonStream` applies it to every JSON stream (OpenAI
  chat / completions / Responses, Gemini, Anthropic messages, Sonar) when a call passes no `maxFrameLength`; the raw
  framings (Anthropic batch results / session events, Bedrock, the Claude CLI's stdout) go through
  `StreamingConsts.framing(delimiter)`, the core SSE decoder takes it as its default. A frame over the cap fails the stream with an `OpenAIScalaClientException` naming the key and the env
  variable (`StreamingConsts.frameTooLong`, which reads the limit from Akka's / ws-client's framing failure). Pinned by
  `StreamingConstsSpec` (core) and `StreamedFrameLengthWireSpec` plus the Gemini / Anthropic streamed wire specs (frames
  of 3-5 MiB). Live 2026-10-06 (`examples/StreamedLargeFramesSmokeTest`, ~$0.10): OpenAI's Responses image generation
  (gpt-5.4-mini, a 1024x1024 medium PNG) streamed a 2.24 MiB partial image and 2.66 MiB `output_item.done` /
  `response.completed` events, Gemini 2.5 Flash Image a 2.78 MiB image event - each over 1.4.0's cap, all read whole
- Requires Akka Streams materializer in implicit scope only when consuming the `Source`

### Model Parameter Conversions
`ChatCompletionSettingsConversions` (in openai-core) automatically adjusts unsupported parameters per model. Every OpenAI rule below was MEASURED on 2026-09-26 against the live chat completions API (41 models x 23 parameter cases, raw and through the client) - re-run `examples/OpenAIConversionsAudit <out.jsonl> <models...>` (and `examples/googlegemini/GeminiThinkingAudit`) when models change; `ModelConversionsRoutingSpec` / `GeminiThinkingSpec` pin the results. "Without reasoning" = no `reasoning_effort` or `none`.
- **Dispatch** (`ChatCompletionBodyMaker`, on the Bedrock-canonical id): o1-preview/o1-mini (`isO1PreviewOrMini`) -> o-series by pattern `o[134](-...)` (`isOSeries`; future-proofing - the ids the old static set missed are Responses-only (o3-pro) or shut down (deep-research)) -> `chat-latest` -> GPT-6 Astra / GPT-6.1+ (`isGpt6ReasoningAlwaysOn`, by the PARSED `gpt6Minor`) -> other `gpt-6-*` (Sol/Luna rules) -> `gpt-5-search-api*` -> GPT-5 by PARSED minor version (`gpt5Minor`: `gpt-5.10` is minor 10, not 1; `gpt-5.4.1-mini` is minor 4; any minor >= 6 gets the 5.6 rules, not the oldest). `gpt5Minor` / `gpt6Minor` share one parser (`versionRegex(major)`: a letter suffix is allowed - `gpt-6o` is GPT-6, `gpt-60` is not), also used by the Responses-side checks (`responsesReasoningEffort`, `responsesSamplingUnsupported`)
- **GPT-6 Sol / Luna** (`gpt6SolLuna` = the GPT-5.6 rules): unlike Astra they accept `reasoning_effort=none` and function tools on chat completions (with `none` only). There is no `gpt-6-terra`; an unknown `gpt-6-*` id gets the Sol/Luna rules
- **GPT-6.1 Sol** (DevDay 2026-09-29, measured 2026-09-29 incl. Bedrock `global.openai.gpt-6.1-sol`): the ASTRA rules (`gpt6`), not Sol's - reasoning always on (`none`/`minimal` rejected on both APIs), function tools Responses-only with every effort. Any `gpt-6.<minor>` with minor >= 1 gets them too (`isGpt6ReasoningAlwaysOn`). Bedrock's chat completions also accept `max` (still sent as `xhigh`) and reject `reasoning.mode` / the `fast` tier
- **Multi-agent execution** (Responses API beta, GPT-6.1 Sol; live-verified 2026-09-30): `CreateModelResponseSettings.multiAgent` -> body `multi_agent` + the REQUIRED `OpenAI-Beta: responses_multi_agent=v1` header (only when enabled: `multi_agent {enabled: false}` is accepted without it - live 2026-09-30) (`CreateModelResponseSettings.betaHeaders`, sync + streamed). OpenAI reads only the FIRST `OpenAI-Beta` header of a request, so the factories no longer send the dead global `assistants=v2` one (it masked the per-call beta -> 400). Items `multi_agent_call` / `multi_agent_call_output` / `agent_message` (`MultiAgentCall` / `MultiAgentCallOutput` / `AgentMessage`, Input + Output) and every message carry `agent: {agent_name}` (`/root` or `/root/<task>`); subagents' messages and deltas interleave with the root's, so `Response.outputMessageContents` / `outputText` and the chat adapter take the root agent only (`subagentMessages` for the rest) and `ChatChunks.fromResponseEvents` drops subagent deltas (their items -> `Other("subagent.message" / "subagent.reasoning")`), maps the calls to server-side `multi_agent.<action>` `ToolCall` / `ToolResult`. `reasoning.summary` + multi-agent -> 400, so the adapter's typed stream asks for no summary then. Chat: `setResponsesMultiAgent` (a Responses-only setting)
- **Responses-only settings** (`chatRequiresResponsesAPI(settings)`): approval decisions, `setResponsesTools`, `setResponsesReasoningMode` (`reasoning.mode` `standard`/`pro` - GPT-6 only; the chat completions API has no such parameter), `setResponsesMultiAgent` and `service_tier = ultrafast` (chat completions 400s it for every model; the Responses API serves it for GPT-6 Astra, access-controlled). The full service routes them to the Responses adapter on every chat entry point (sync, tools, OpenAI-shaped and typed streams - the OpenAI-shaped stream still refuses decisions / Responses-native tools); chat-only services refuse the first three and pass `ultrafast` to the API. `ServiceTier` has `priority` / `fast` (same Fast mode; GPT-6 responses report `fast`) and `ultrafast`; `ReasoningConfig` has `mode` / `context` (tolerant reads - an unknown effort / mode / context in a Response reads as absent)
- **Tool routing**: `chatToolsPreferResponsesAPI(settings, tools)` - on a Responses-capable service (the full `OpenAIService`, sync and typed streamed) tool calls go through the Responses API for GPT-6 Astra / 6.1 Sol ALWAYS, for GPT-5.6+ / GPT-6 Sol/Luna unless `reasoning_effort = none`, and for GPT-5.4 / 5.5 when an explicit effort other than `none` is set (`chatToolsRejectExplicitReasoning`) - so reasoning survives with tools. A chat-only service forces `none` (5.6+, `gpt5_6ChatTools`) or drops the effort (5.4 / 5.5, `gpt5_5ChatTools`; `none` is kept); Astra fails fast. The Responses adapter applies its own rules: `responsesReasoningEffort` (`max` kept, `minimal`->`low`, Astra / 6.1 `none`->`low`) and `responsesSamplingUnsupported` (temperature/top_p/top_logprobs dropped on GPT-5.6+/6)
- **GPT-6 (Astra)**: all sampling params restricted, `max_tokens->max_completion_tokens`, effort low..xhigh (`max`->`xhigh`, `minimal`/`none`->`low`); function tools only on the Responses API
- **GPT-5.6** (and newer minors): all sampling params restricted; effort none..xhigh (`max`->`xhigh`, `minimal`->`low`); tools on chat completions need `none`
- **GPT-5.5**: all sampling params restricted; effort none..xhigh (`max`->`xhigh`, `minimal`->`low`)
- **GPT-5.4**: sampling params, penalties and `logprobs` restricted only with reasoning - except that `logprobs` is always dropped for the dated `gpt-5.4-2026-03-05` / `gpt-5.4-mini-2026-03-17` snapshots (403 even without reasoning; the aliases and the nano snapshot accept it); effort none..xhigh
- **GPT-5.2**: sampling params, penalties and logprobs restricted only with reasoning; effort none..xhigh (`minimal`->`low`, `max`->`xhigh`)
- **GPT-5.1**: as 5.2 but effort none..high (`minimal`->`low`, `xhigh`/`max`->`high`)
- **GPT-5 / -mini / -nano**: all sampling params restricted, logprobs a 403; effort minimal..high (`none`->`minimal`, `xhigh`/`max`->`high`)
- **gpt-5-search-api**: temperature / top_p / penalties rejected EVEN at their defaults (dropped, not clamped), logprobs and reasoning_effort unknown (dropped), verbosity `medium` only; function tools unsupported - tool completions fail fast with an `OpenAIScalaClientException` (`chatToolsUnsupported`), sync and streamed
- **GPT-5.3**: no live chat model (`gpt-5.3-codex` is Responses-only); rules kept as before
- **O-series** (o1, o3, o3-mini, o4-mini): `max_tokens->max_completion_tokens`, temperature=1, top_p=1, penalties=0, logprobs dropped (403), no parallel tool calls, verbosity medium only; effort low..xhigh (`none`/`minimal`->`low`, `max`->`xhigh`)
- **Responses-only on OpenAI** (chat completions 400/404): `gpt-5-pro`, `gpt-5.2/5.4/5.5-pro`, `o1-pro`, `o3-pro`, `gpt-5.3-codex` - use the Responses API / `OpenAIResponsesChatCompletionService`
- **`stop` / `logit_bias`** (measured 2026-09-30, 37 models, raw + `OpenAIConversionsAudit`): dropped with a warning (`stopUnsupported` / `logitBiasUnsupported`) on every GPT-5.x / GPT-6 rule set (rejected with ANY effort, `none` included), `chat-latest` and `gpt-5-search-api`; the o-series drops `logit_bias` always and `stop` except on `o1` / `o3-mini` (+ dated snapshots; `stopUnsupportedOnNewerOSeries`). Bedrock's OpenAI models reject `stop` and accept-but-IGNORE `logit_bias` (a +100 bias changes nothing), so the shared canonical-id rules fit them too. The Responses adapter already warns and drops both (no such parameters there)
- **Non-reasoning models** (gpt-3.5 / 4 / 4o / 4.1) get no conversion: a `reasoning_effort` / `verbosity` there is left to the API's clear 400
- **Gemini thinking** (`gemini/service/impl/GeminiThinking`): levels for Gemini 3.x, the rolling aliases (`gemini-flash-latest`, `-flash-lite-latest`, `-pro-latest`) and `nano-banana-pro*`; budgets for 2.5; nothing for `gemini-2.5-flash-image`. MINIMAL is rejected by the Pro models (not 3-pro-image), 3.7 / 3.8 Flash and `gemini-flash-latest`; the 3.1 Flash image models accept only MINIMAL / HIGH. `gemini-omni-*` only serve the Interactions API (not usable by the adapter)
- **JSON-schema model matching** (`handleOutputJsonSchema`): exact id, `-<id>` suffix, and for OpenAI-on-Bedrock ids the canonical id (`us.openai.gpt-5.6-luna` matches `gpt-5.6-luna`); a Bedrock Anthropic id never matches its bare Claude name (Bedrock rejects `output_config.format` for 4.7+ / 5.x, and so does the mantle short form `anthropic.claude-haiku-4-5`)
- **Anthropic max-output table**: the LONGEST matching id wins, so the `contains`-matched table's order no longer matters
- **Mistral** (live-verified 2026-10-07): the reasoning models take `reasoning_effort` as a switch - Large 4 (`mistral-large-4` / `-4-0`) and every alias of Medium 3.5 / Small 4 (`mistral-medium*`, `magistral-*`, `mistral-small-2603` / `-latest`, `mistral-vibe-cli-*`) only `none` / `high` (`mistralSwitchReasoning`: minimal -> none, low / medium / xhigh / max -> high), Mistral-hosted GLM 5.3 (`zai-glm-5` / `-5-3` / `-latest`) only `low` / `high` / `max` (`mistralGlm`: none / minimal -> low, medium -> high, xhigh -> max; it cannot turn reasoning off); GLM 5.2 takes every value, Large 3 (`mistral-large-2512` = `mistral-large-latest`) none (its 400 is left to the API). Large 4 and GLM 5.3 reason by default. A reasoning turn's `content` is a LIST of chunks - `{"type": "thinking", "thinking": [{"type": "text", ...}]}` + `{"type": "text", ...}`, a stream delta carrying one or both - which `JsonFormats.withChunkedContent` reads into the text (`AssistantMessage` / `AssistantToolMessage` content) and, for deltas, `reasoning_content` (-> `Thinking` on the typed stream); chunks of other kinds - Mistral's `reference` citations (live 2026-10-09: Medium 3.5 / Magistral stream one as a delta of its OWN, the non-reasoning Large 3 answers `[text, reference, text]`) - ride raw in `ChunkMessageSpec.content_chunks` -> `ChatChunk.Other("content.reference", chunk)` on the typed stream, and are dropped by the sync message reads (no field for them); a list without text reads as no content - a 200 never fails on an unmodelled chunk kind (the 2026-10-08 'refuse loudly' reader killed Medium 3.5's citation streams with `error.expected.jsstring`; pinned by `MistralChunkedContentSpec` with the live `medium-3.5-citations-stream.txt`); before 2026-10-07 any reasoning answer from Mistral failed to parse. Sync responses carry no reasoning for any provider (a known limitation - `MessageConversions`' deprecation text now says so). Large 4: 512k context per `/v1/models` (1M per the docs), vision, json_schema (in `models-supporting-json-schema`), tools auto / required / named, prefix; `max_completion_tokens` is a 422; a 1 x 1 image reads as black (use a real one). Pinned by `MistralChunkedContentSpec` (recorded responses in `openai-core/src/test/resources/mistral/`) and `ModelConversionsRoutingSpec`; live walkthrough `examples/mistral/MistralLarge4SmokeTest` (all 8 passed).
- **Groq**: DeepSeek R1 models need `max_completion_tokens` and optional reasoning format. Groq's catalog on 2026-10-09 (12 models): `openai/gpt-oss-120b` / `-20b` / `-safeguard-20b`, `qwen/qwen3.6-27b` / `qwen3.8-27b`, `allam-2-7b`, the two prompt-guard models, two Orpheus TTS and two Whisper models - the Llama 3.3 / 4 and DeepSeek ids are gone (404), and so are `groq/compound` / `-mini` (404 `model_not_found`; constants `@deprecated` since 1.5.0, `NewModelsSmokeTest` expects the failure)

### Domain Model Organization
Domain classes are in openai-core/src/main/scala/io/cequence/openaiscala/domain/:
- **BaseMessage** and subtypes (SystemMessage, UserMessage, AssistantMessage, etc.)
- **ModelId** - OpenAI model IDs; **NonOpenAIModelId** - third-party model IDs (Claude, Gemini, Grok, Llama, Mistral, etc.)
- **settings/** - Request settings classes (CreateChatCompletionSettings, JsonSchemaDef, WebSearchOptions, etc.)
- **response/** - Response types (ChatCompletionResponse, TracedBlock, etc.)
- **responsesapi/** - Responses API types (Input, Response, Output, Reasoning, etc.)
- **responsesapi/tools/** - Tool definitions (FunctionTool, FileSearchTool, WebSearchTool, ComputerUseTool, CodeInterpreterTool, ImageGenerationTool, LocalShellTool, CustomTool)
- **responsesapi/tools/mcp/** - MCP integration (MCPTool with predefined connectors for Dropbox, Gmail, Google Drive, etc.)
- **graders/** - Evaluation graders (StringGrader, PythonGrader, ScoreModelGrader, LabelModelGrader, TextSimilarityGrader, MultiGrader)
- **guardrails/** - Guardrail checks, verdicts, actions and `ModelGuardrailSettings` (the adapter + `ModelGuardrail` are in `service/guardrails/` and `service/adapter/`)
- **ChatCompletionInterceptData** / **ChatCompletionErrorInterceptData** - Adapter interception data with timing
- Tool/function calling: ChatCompletionTool, FunctionSpec, JsonSchema
- Assistant types (Assistant, Thread, Run, RunStep, etc.)
- Vector store types (VectorStore, VectorStoreFile, etc.)
- Batch processing types

### JSON Handling
JSON serialization/deserialization uses Play JSON:
- **openai-core**: `JsonFormats.scala` for core types; `domain/responsesapi/JsonFormats.scala` and `domain/responsesapi/tools/JsonFormats.scala` for Responses API; `domain/graders/JsonFormats.scala` for graders
- **anthropic-client**: `anthropic/JsonFormats.scala` for Anthropic-specific types
- **google-gemini-client**: Gemini-specific JSON formats in service impl package

When adding new domain classes, update the appropriate JsonFormats with Format instances.

**JSON schema from a case class** - `JsonSchemaReflectionHelper.jsonSchemaFor[T](dateAsNumber, explicitTypes)` (trait +
companion object):
- **How:** Scala 2 uses runtime reflection (`scala-2/`, plus Scala 2's `useRuntimeMirror`); Scala 3 uses a quotes
  macro (`scala-3/`, `JsonSchemaMacros`). Both build the shared IR `service/JsonSchemaShape`, which one converter
  (`toJsonSchema`) turns into a `JsonSchema`, so the versions cannot drift - `JsonSchemaDerivationSpec` runs on both.
- **Types:** primitives / boxed / `BigInt` / `BigDecimal` / UUID / `java.time` (temporals, `Duration`, `Period`,
  `ZoneId`) / Scala `Duration` / `URI` / `URL` / `Locale` / `Currency` / `File` / `Path` (strings), `java.util.Date`
  (string, or a number); `Option` / `java.util.Optional` = not required; any Scala or Java `Iterable` / `Array` = array;
  a value class (`extends AnyVal`) = its underlying type, as `Json.valueFormat` writes it; `Map[String, V]` /
  `java.util.Map` = an open object (`additionalProperties: true`, the value type not expressed - OpenAI's strict mode
  closes every object, so a map needs `strict = false`; the TypeSafe planner refuses it as "an object without
  properties"); type params resolved; decoded field names. (Value classes, maps, Java collections / `Optional` and the
  extra string types since 2026-10-09 - a value class used to derive as an object `{value}`.)
- **Enums:** `Enumeration` / Java enum / Scala 3 `enum` keep declaration order; sealed case objects are sorted by
  `toString`. `@JsonSchemaDescription` on a case object or Scala 3 enum case
  (`EnumShape.descriptions`) is appended to the field's description as "- value: description" lines. A JSON
  schema enum has no place for value descriptions, so this is the only route; for a `Seq` of an enum the lines
  go on the array's description. The TypeSafe planner splits them back out (`JsonSchemaShape.splitValueDescriptions`):
  a choice gets them as its per-option `criteria`, each multi-select option's question only its own line (every
  option carrying every line grew as N^2 - ~40 described options would fill Jev's 32k input). Live 2026-10-04
  (`TypeSafeTypedDecision`): descriptions sharpened the answers (Login 95 -> 99%, Sales 96 -> 100%); 2026-10-06 the
  split layout gave the same answers (teams 100%, topics within 1-8 points) for ~47 fewer input tokens per call
  at 3 options (522 vs 569).
- **Annotations:** `domain/JsonSchemaAnnotations` - `@JsonSchemaDescription` (class or field) and
  `@JsonSchemaRange(min, max)` (an integer gets ceil / floor; it also applies to a numeric collection's items).
- **Leftover:** the Scala 2 / 3 `service/ReflectionUtil` objects (public in 1.4.0) lost their last user to the IR
  rewrite - `@deprecated` since 1.5.0, to be removed later.
- **Refusals:** an `Either` ("no anyOf here"), a tuple, a sealed hierarchy with case classes, or a recursive type. Scala 2 throws
  `OpenAIScalaClientException`; Scala 3 fails at compile time (tested with `typeCheckErrors`). Not refused: an
  `Enumeration` whose values cannot be reached (declared in a class, a generic `E#Value`) or a Java enum the mirror's
  class loader cannot see stays a plain string, as 1.4.0 derived it; Scala 2 names case objects it cannot reach
  (declared in a class) by their names.
- **Scala 3 macro gotcha:** never select a member on a spliced tree. `${x}.toString` fails for a `toString` declared
  without parentheses, and `${cls}.getEnumConstants` fails to re-type, so read values through plain helpers
  (`JsonSchemaShape.javaEnumValues` / `enumerationValues`, `String.valueOf`).
- **Live:** `examples/CreateChatCompletionJsonForCaseClass` (now cross-version) passed with strict structured output
  on gpt-5.4-mini on 2.13 and 3 (2026-10-02).
- **`JsonSchemaOf[T]`** (`service/JsonSchemaOf`): the schema of a type for APIs that need one per type. The
  instance is derived per version (`JsonSchemaOfDerivation`: Scala 2 `implicit def derived[T: TypeTag]`, Scala 3
  `inline given derived[T]` - a compile error for an unsupported type). A local instance
  (`JsonSchemaOf.instance(schema)`) wins over the derived one on both versions.

### Configuration
Configuration uses Typesafe Config with defaults in `openai-scala-client.conf`:
- API keys via env vars: `OPENAI_SCALA_CLIENT_API_KEY`, `OPENAI_SCALA_CLIENT_ORG_ID`
- Provider-specific keys: `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN`, `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE`, `CLAUDE_CODE_OAUTH_TOKEN`, `VERTEXAI_PROJECT_ID`, `GOOGLE_API_KEY`, etc.
- Timeout settings: `requestTimeoutSec`, `readTimeoutSec`, `connectTimeoutSec`, `pooledConnectionIdleTimeoutSec`
- `models-supporting-json-schema` - List of models that support JSON schema structured output (GPT-5.x, GPT-4.x, O-series, Claude, Gemini, Grok, etc.)
- `reasoning-effort-thinking-budget-mapping` - Maps reasoning effort levels (none/minimal/low/medium/high) to provider-specific thinking budgets (Gemini thinking_budget tokens, Anthropic budget_tokens)

## Development Workflow

1. **Before committing**: Run `sbt formatCode` to auto-format code
2. **Before pushing**: Run `sbt validateCode` to ensure CI will pass
3. **When adding features**: Add examples in openai-examples module and document them on the matching `docs/` page (the README is only the index)
4. **When changing domain**: Update the appropriate JsonFormats.scala with JSON codecs
5. **When adding provider support**: Create new client module following anthropic-client pattern
6. **When adding new models**: Update `ModelId.scala` (or `NonOpenAIModelId.scala`), add to `models-supporting-json-schema` in config if JSON schema is supported, add parameter conversions in `ChatCompletionSettingsConversions` if needed, and add the model prefix handling in `OpenAIChatCompletionServiceImpl`

## Testing Strategy

- Unit tests use ScalaTest with ScalaMock
- An aggregated run (`sbt test`, `client_stream/test`) needs `OPENAI_SCALA_CLIENT_API_KEY` set (any value): openai-client's TEST copy of `openai-scala-client.conf` requires it, and a core test running on a pool thread created by another module's tests can load that copy through the thread's context classloader (`HasOpenAIConfig`) - 17 `ModelGuardrailSpec` cases then fail with `UnresolvedSubstitution` (seen 2026-10-07; `core/test` alone passes)
- Integration tests require API keys set as environment variables
- Use `testOnly` for focused test runs during development
- CI runs full test suite across all Scala versions with coverage reporting

## Common Patterns

### Creating a Service
```scala
implicit val ec = ExecutionContext.global

val service = OpenAIServiceFactory() // uses env vars
// or
val service = OpenAIServiceFactory(apiKey = "sk-...")
```

Since the ws-client 1.0 engine-discovery migration (`io.cequence:ws-client-*`, a real Maven
Central release since 2026-07 - no longer a SNAPSHOT; currently 1.1.1, see
`project/Dependencies.scala`), EACH service created via the plain factories owns a dedicated ActorSystem (created
eagerly, ~10 threads; its threads are daemon so a leaked service cannot block JVM exit).
`service.close()` terminates both the HTTP client and that system. Build services ONCE and
share the service instance - do NOT construct services per request. A caller-supplied
Materializer is no longer accepted (or needed) by the factories. Timeouts are client-level:
they ride in `TransportSettings` (factories take `timeouts: Option[Timeouts]`), NOT in
`WsRequestContext` (which since ws-client 1.0 carries only per-request data: authHeaders,
extraParams).

**Streamed errors (ws-client 1.1.1).** A non-2xx answer to a streamed request fails the stream
with a structured `CequenceWSHttpStatusException(statusCode, body)` - it is NOT emitted as
stream data. Every streamed service extends `ClassifiedStreamingWSClient` (openai-core) and
streams through its SERVICE-level `execJsonStream` / `execRawStream(endPoint, ...)` (ws-client's
`WSClientWithEngineOutputStreamingBase`), which route that failure through the service's
`handleErrorCodes` (`mapHttpStatusErrors`); free-form JSON bodies go in as `Param.Raw(name)`.
Do NOT call `engine.execJsonStream(site, ...)` directly - it fails with the UNCLASSIFIED
exception that `Retryable` ignores; `StreamErrorMappingConventionSpec` (openai-core tests) scans
every module's main sources and fails on an engine-level stream call not followed by
`.mapError(mapHttpStatusErrors)`. An `{"error": ...}` frame inside a 200 stream (e.g. a
mid-stream `overloaded_error` / `server_error` / Gemini `UNAVAILABLE`) goes through
`inBandStreamError`, which reads the status from the frame's numeric `code`, Google `status` or
OpenAI / Anthropic `type` (`InBandStreamErrors`) and classifies it like that HTTP status. An
OpenAI adapter over a native service must also repack errors raised DURING its streams
(`.mapError(toOpenAIException)` - Anthropic and Gemini `impl` package objects), not just the
setup future. Pinned against a local server by `StreamedHttpErrorsWireSpec` (OpenAI),
`AnthropicStreamedHttpErrorsWireSpec` (incl. Managed Agents session events),
`GeminiStreamedHttpErrorsWireSpec` and `SonarAgentWireSpec` (Perplexity). Vertex AI chat never
touches ws-client (Google SDK, gRPC): its failures are classified by canonical status in
`vertexai/service/impl/VertexAIErrors` (also used by the batch-prediction REST service's
`handleErrorCodes` via `error.status`, falling back to the shared `OpenAIErrorCodes` HTTP policy in openai-core) and pinned end
to end by `VertexAIStreamedErrorsSpec`, which drives the real SDK over a scripted
`PredictionServiceStub` (`VertexAI.Builder.setPredictionClientSupplier`).

**Akka backend, for now.** This project currently hard-wires its streaming API surface to Akka
Streams - `createChatCompletionStreamed` etc. return `Source[T, akka.NotUsed]`, and every
provider module depends on the Akka-flavored ws-client artifacts (`ws-client-core-akka`,
`ws-client-play-akka`, `ws-client-play-akka-stream`). ws-client itself is no longer
Akka-only: it also ships Pekko engines (`ws-client-core-pekko`, `ws-client-play-pekko`,
`ws-client-play-pekko-stream`), backend-only engines with no actor system at all
(`ws-client-jdk`, `ws-client-sttp`), and a family-neutral streaming core
(`WSClientOutputStreamCore`, `java.util.concurrent.Flow.Publisher`-typed) that every engine
implements natively. A live experiment (2026-07-14) swapped `ws-client-play-akka` →
`ws-client-play-pekko` for **sync** calls with zero source changes (same FQCNs, engine loaded
from the Pekko jar, ran on `pekko.actor.default-dispatcher` threads). **Streaming is
currently family-locked**: this repo's streamed traits pin `Source[_, akka.NotUsed]` and ~15
`akka.*` imports in `openai-core`/`openai-client-stream`, so a full Pekko (or backend-agnostic)
swap needs a mechanical rename, not just a dependency bump. Expect this to be abstracted away
in a future release - don't assume `Source`/`akka.NotUsed` in new public APIs if avoidable, and
prefer routing new streaming code through the same choke points (`WSClientOutputStreamExtraAkka`)
so the eventual swap stays mechanical.

Engines are SITE-STATELESS since ws-client 1.0: an engine is just the HTTP client + pool +
actor system; each SERVICE holds a `SiteBinding` (base URL, auth, error recovery, label) and
feeds it into every call. To share ONE engine across MANY services - including across
DIFFERENT providers:
```scala
import io.cequence.wsclient.service.spi.StreamedEngineRegistry

val engine = StreamedEngineRegistry.outputStreamed() // one pool + one (daemon) actor system

val openAI = OpenAIServiceFactory.withEngine(engine)            // api key from config/env
val anthropic = AnthropicServiceFactory.withEngine(engine)      // api key from env
val gemini = GeminiServiceFactory.withEngine(engine)            // api key from env
// also: SonarServiceFactory.withEngine, VertexAIServiceFactory.batchPredictionWithEngine,
// withEngine(engine, coreUrl, requestContext) on the OpenAI-shaped factories (custom
// gateways), and .withStreaming.withEngine(...) for merged sync+streamed services

anthropic.close() // a service on a shared engine does NOT close it; openAI keeps working
engine.close()    // the one real teardown - close it once, when done with all services
```
The plain factories (`OpenAIServiceFactory()`, `AnthropicServiceFactory()` etc.) create a
PRIVATE engine per service and close it with the service - semantics unchanged.

**Timeouts override on a shared engine.** Timeouts/proxy are baked into the engine's HTTP
client at construction (`TransportSettings(timeouts: Timeouts, proxyURL: Option[String])`,
deliberately NOT overridable per-site/per-call - see `TransportSettings`'s scaladoc). All four
`Timeouts` fields (`requestTimeout`/`readTimeout`/`connectTimeout`/`pooledConnectionIdleTimeout`)
are **milliseconds** (`Option[Int]`) - only the `*Sec`-suffixed config-file keys
(`requestTimeoutSec` etc., see `openai-scala-client.conf`) are in seconds; the config loader
multiplies by 1000 before building `Timeouts`. A service that needs different client-level
settings than the rest of a shared setup uses an engine COPY via
`WSClientEngine#copy(transportSettings, reuseExecContext = true)`:
```scala
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts

val engine = StreamedEngineRegistry.outputStreamed() // default timeouts, one actor system

// same actor system, own HTTP client with longer timeouts - e.g. for a slow batch/VLM provider
val slowEngine = engine.copy(
  TransportSettings(timeouts = Timeouts(
    requestTimeout = Some(300000),   // 300s
    readTimeout = Some(300000),      // 300s
    connectTimeout = Some(20000),    // 20s
    pooledConnectionIdleTimeout = Some(60000) // 60s
  ))
)

val fastService = OpenAIServiceFactory.withEngine(engine)
val slowService = AnthropicServiceFactory.withEngine(slowEngine)

slowService.close() // closes only slowEngine's own HTTP client, not the shared actor system
fastService.close()
engine.close()       // teardown the shared actor system last
```
`reuseExecContext = false` instead gives the copy its OWN actor system too (fully independent;
only supported for discovery-created engines, throws for a caller-supplied one) - rarely
needed, since the whole point of copying is usually to avoid paying for a second actor system.
`withStreaming` factory composition builds ONE engine per provider (it used to build two). To
embed in an existing akka app, build the engine with a ws-client direct constructor on YOUR
materializer (e.g. `PlayWSStreamClientEngine()`) and pass it to `withEngine` - closing that
service never touches your ActorSystem.

### Using Adapters
```scala
val adapters = OpenAIServiceAdapters.forFullService
val service1 = OpenAIServiceFactory(apiKey1)
val service2 = OpenAIServiceFactory(apiKey2)

val loadBalanced = adapters.roundRobin(service1, service2)
val withRetry = adapters.retry(loadBalanced, Some(println))
```

### Responses API
The Responses API provides a unified interface with tool support (file search, web search, functions, MCP, computer use, code interpreter, image generation, local shell). Key types are in `domain/responsesapi/` package.

### Agents API (beta)
`OpenAIAgentsService` (core `service/`, part of the full `OpenAIService`; impl `openai-client/.../impl/OpenAIAgentsServiceImpl`, delegated by `OpenAIServiceWrapper`; domain + JSON in `domain/agents/`): agents, sessions, input events, items / turns / subagents, artifacts, environment templates / files (templates and files as JSON). Every call sends `OpenAI-Beta: agents=v1` (`OpenAIAgentsServiceImpl.betaHeaders`) - OpenAI reads only the FIRST `OpenAI-Beta` header, so nothing may send a global one. Streaming (`OpenAIStreamedServiceExtra`, impl `OpenAICoreServiceStreamedExtraImpl`): `createAgentSessionStreamed` (POST `stream: true`; the server closes it after `agent.session.idle`) and `streamAgentSessionEvents` (GET `/events`, held open with `:` heartbeats every 15 s - ws-client's JSON framing cannot take comment-only frames, so both go through `execRawStream` + core `service/ServerSentEvents` (moved from the Perplexity module, which forwards to it with its own exception; it searches only `carry ++ chunk` - the pending event's last 3 bytes plus the new chunk - for the next blank line and never walks the event's ByteString rope per chunk: a whole-buffer `indexOfSlice` was cubic in the chunks (1 MiB in 8 KiB chunks: 21 s, 2026-10-08), a tail search that `drop`ped into the rope still quadratic (64 MiB in 1460-byte chunks: 11 s vs 0.9 s with the carry, 2026-10-09); an event is then read on its bytes - one compaction, the `data:` lines sliced, `Json.parse(bytes)` - instead of ~9 String copies (~10x the event's size in garbage); its behaviour suite is core `ServerSentEventsSpec` (incl. a 2 MiB event in 8 KiB chunks), the Perplexity spec pins only its exception type)); `AgentSessionEvents.untilSettled` ends a subscription at the idle / failed after a finished turn (optionally at `requires_action`), `finalAnswer` collects the `final_answer` messages. Reads are lenient: unknown tools / items / events / required actions -> `Raw` / `Other`. Live facts (2026-09-30, recorded in `openai-core/src/test/resources/agents/`): assistant messages carry `phase` `commentary` / `final_answer`; a client function call -> `requires_action` with `required_actions[].arguments` a JSON OBJECT; the POST stream stays open during `requires_action` (post the `tool_result` and keep consuming; items of the same output may still follow the event); a subscription opened on an idle session gets only the next turn (subscribe, then send), opened mid-turn it replays the turn's items WITHOUT deltas (`output_text.done` only); DELETE of a session paused mid-turn -> 409 (cancel first); `turn.completed` usage is null; `GET /agents` is eventually consistent (a new agent is missing at 2 s, listed by 10 s); multi-agent sessions add `create_subagent_call` / `wait_for_subagents_call` / `close_subagent_call` / `agent_message` items and `agent.session.subagent.*` events. Tests: `AgentsApiJsonSpec` (core, recorded payloads + request bodies), `AgentsApiWireSpec` (client-stream, local server, heartbeat + CRLF framing); live `examples/agents/OpenAIAgentsApiSmokeTest` (`hosted` arg runs a sandbox).
Chat adapter: `service.agentsAsChatCompletion(agentId, environment, agentTools, multiAgent, deleteSessionsAfterUse)` (client-stream implicits) = core `service/adapter/OpenAIAgentsChatCompletionService` (needs an implicit Materializer): one session per call (inline agent from the settings - system messages -> instructions, `responsesReasoningEffort`-converted effort, verbosity + json_schema -> `text` (`JsonSchema.setAdditionalPropertiesToFalse`: the API validates schemas strictly - live 400 "additionalProperties must be false for a strict schema"), service tier, function / MCP tools + `agentTools`; or `agentId`, then per-call tools refused), history folded into ONE labeled user message; `TurnState` maps events: `final_answer` -> Text, `commentary` / reasoning summaries -> Thinking, commands (+ `CodeExecution*`), MCP calls, web searches, subagent calls -> server-side ToolCall / ToolResult, environment / subagent events -> Other; ends at idle-after-turn (Finish stop) or at a NEW `requires_action` (client calls -> Finish tool_calls, session KEPT in the adapter's `pausedSessions`); a call ending with ToolMessages for a paused session resumes it: GET `/events` FIRST, post the `tool_result`s on the first replayed event (or after 3 s - a turn that finished before the subscription connected is never delivered), skip the replayed items already reported, done-only text (no deltas) emitted whole; the replayed pause itself (all calls answered) is not a new pause. End-of-stream detection uses a sentinel element (NOT `concat(Source.lazySource(...))` - Akka's `concat` demands its second source eagerly). A turn is settled at its end marker, synchronously in the stream's `mapConcat`, BEFORE its final chunks go out (`finishTurn`): a pause is registered at once; a finished session's DELETE (cancel + DELETE when the turn ended cancelled) is started - after a resume's POST outcome is known - and tracked in `cleanupsInFlight`; the stream completes only after the settlement, and `close()` waits (30 s cap) for every tracked cleanup. So neither a resume nor `close()` issued by a caller who has seen the `Finish` - even one that stopped reading there - can overtake it (registering from `watchTermination` raced an immediate resume, which then started a new session; the wire spec reproduced it with a 700 ms-delayed callback, and `close()` right after an early-stopped stream lost the DELETE). A turn that did not reach its end (failed, or the consumer cancelled) is cancelled, then deleted from the termination callback (DELETE mid-turn is a 409). A paused session stays registered until the resume's results are POSTED (a retry right after a failed resume resumes the same session), and the results are never posted once the resume stream is gone (the 3 s grace timer checks). `close()` cancels + deletes paused sessions. With `agentId`, `Start` reports the agent's own model (from the session), the call's effort / verbosity / tier / json_schema are ignored with a warning, and `agentTools` / `multiAgent` are refused at construction. `error` session events are classified by their code like failed turns (`classifiedError`: overloaded / rate limit / server error retryable). The json_schema is always closed, whatever `strict` says (typed via `setAdditionalPropertiesToFalse(overrideExisting = true)`, the map form via `OpenAIChatCompletionExtra.toStrictSchema`). A deprecated `MessageSpec` is folded by role (developer ones are instructions). Refused: approval decisions, `setResponsesTools`, SkillTool, MCPServerTool(requireApproval), file content parts (the API takes text and images only). Wire: `AgentsChatAdapterWireSpec` (recorded pause + replay). Live: a tool loop (pause + resume) takes ~12 s (runs of ~75 s seen once - platform variance).

### Decisions API (public beta)
`OpenAIDecisionsService.createDecision(input, questions, settings)` (core `service/`, part of the full `OpenAIService`;
impl `openai-client/.../impl/OpenAIDecisionsServiceImpl`, `POST v1/decisions`; domain + JSON in `domain/decisions/`), launched
2026-10-06: typed answers about text and inline images - `DecisionQuestion.Predicate` (probability), `Choice` (one of fixed
options, values text or boolean: `DecisionValue.Text` / `Bool`), `Score` (ordered levels -> the probability-weighted average
of their 0-based indices) - each answer a distribution with a `confidence`, plus `DecisionAnswer.Refusal` (one question
declined, the others answered) and `Unknown` (a future type, kept raw). `gpt-6-luna` only; $0.10 / 1M input tokens, no
output charge (output_tokens 0). The usage reuses the Responses API's `UsageInfo` (its `cache_write_tokens` is dropped).
Live facts (2026-10-07, probes + `examples/typesafe/OpenAIDecisionsSmokeTest`): the response is `{model, answers, usage}`
(no id - the request id is the `x-request-id` header); an unnamed question's answer has `name: null`, in question order;
probabilities come rounded to 2 decimals; at most 200 questions (400 `array_above_max_length`), 255 choices, 10 levels;
duplicate names are a 400, empty instructions and an empty name are accepted; images only as base64 data URLs (an http(s)
URL is a 400 "pattern '^data:'"), at most 128; only user messages (an assistant role is a 400); another model is a 404
`model_not_found`; an unknown field a 400 `unknown_parameter`; 300k input tokens passed (1.7 s), 1.2M is a 400 "Decision
input exceeds the token limit."; ~200-300 ms for a few questions, ~500 ms for 40 (~150 input tokens per question).
Against Jev (live 2026-10-07, the same three questions on four tickets): the teams agree, urgency within ~0.25; OpenAI
bills the input once PER QUESTION (176 / 410 / 1,481 / 6,071 input tokens at 1 / 3 / 10 / 40 questions) where Jev reads
it once (294 / 406 / 518 / 1,025) - ~2.4x Jev's cost at 3 questions, ~14x at 40 - and slows with the count (247 / 245 /
351 / 553 ms) while Jev stays flat (226-259 ms); Jev rounds its probabilities to 2 decimals too. The
decision routines run on it through the typesafe-client preset `DecisionProviderSettings.openAI` (below). Tests:
`DecisionsJsonSpec` (core), `DecisionsWireSpec` (client-stream).
**Switching APIs** (2026-10-07, the user's ask: either API on either host): `OpenAIDecisionsService` is a service of its
own (`extends CloseableService`, like `OpenAIChatCompletionService`) that `OpenAIService` extends.
- OpenAI's interface on a System One host: typesafe-client's `TypeSafeServiceFactory.asOpenAIDecisions(provider |
  service[, imageInput])`. `TypeSafeServiceImpl` implements `OpenAIDecisionsService` itself - natively for
  `DecisionProtocol.OpenAI`, else via `impl/DecisionTranslation`'s `OpenAIToSystemOne` (images refused when the
  provider reads none) - and so does `TypeSafeServiceAdapters.retry` over such a service (an anonymous
  `RetryTypeSafeService with OpenAIDecisionsService`, retried by the shared `Retryable` matcher), so
  `asOpenAIDecisions(retry(service))` keeps the service's own path - `imageInput` is then ignored, with a WARN when set (review 2026-10-08: it used to fall into the
  translation, which on OpenAI's host dropped `safety_identifier` / image `detail` and merged the messages). Any
  other `TypeSafeService` gets `impl/OpenAIDecisionsOverTypeSafe` - a translation even when its host is OpenAI's.
  Failures are `OpenAIScala*` (`repackAsOpenAIException`, native cause). `Decision.requestId` carries the host's request id on every path - `x-request-id` on OpenAI, the provider's `requestIdHeaders` on a System One host (`x-typesafe-request-id`, OpenRouter's `x-generation-id`, none on llama.cpp; one case-insensitive, blank-skipping lookup for every module: core `ResponseHeaders.first`) (`OpenAIDecisionsServiceImpl` reads it from the rich response, `TypeSafeServiceImpl`
  on both protocols, the translation from `SystemOneResponse.requestId`).
- System One's interface on OpenAI: `DecisionProviderSettings.openAI` (`SystemOneToOpenAI`, behind the codec).
- `CreateDecisionSettings.model` is an `Option`: none = the service's default (`gpt-6-luna` on OpenAI, the host's
  default model elsewhere) - a `String` default would have sent `gpt-6-luna` to Jev.
- Translation: predicate <-> noul, choice <-> choice (a boolean value as its text, so `true` and `"true"` in one
  question are refused), score <-> score (`{label, description}` levels); unnamed / `""`-named questions keyed
  `question_<n>` (around the given names), duplicates refused; answers in question order with names, values and
  labels as asked; `UnknownAnswer("refusal")` <-> refusal; usage as the Responses API's. A message -> its text or an
  array of its parts (`DecisionImage.messageState`, shared with the chat mapping), several -> an array. The
  `safety_identifier` and image `detail` are dropped with a warning.
- Live (`examples/typesafe/DecisionApiSwitchSmokeTest`, all passed): the same `createDecision` call on OpenAI and on
  Jev (refund 1.0 / 0.99, urgency 1.79 / 1.78, the unnamed boolean choice true 0.63 / 0.64), the same `systemOne`
  call on Jev and on OpenAI. Pinned by `OpenAIDecisionsSwitchSpec` and two `TypeSafeServiceWireSpec` cases.

## Important Notes

- Always close services with `service.close()` to release resources
- Use an implicit `ExecutionContext` for async operations. `Materializer` is NOT needed to
  construct or call a service (discovery-created engines own their execution environment) -
  only bring one in if YOU are consuming a returned `Source` (e.g. `.runWith(Sink.foreach(...))`
  on a streamed chat completion)
- The library uses Play WS backend but is designed to be swappable
- Function names match OpenAI API endpoint names in camelCase for consistency
- Provider adapters may have limited feature support - check provider compatibility table in README
- When working with structured/JSON output, use `JsonSchema` and `JsonSchemaDef` for type-safe schemas. `JsonSchema.Integer` / `Number` carry optional `minimum` / `maximum` / `enum` (added 2026-09-17, for the TypeSafe adapter's score levels). Provider support is uneven (live-verified 2026-09-17): OpenAI honours min/max and enum in STRICT mode only (accepted but ignored when `strict = false`); Anthropic 400s on min/max ("For 'integer' type, properties maximum, minimum are not supported"), so `toAnthropicSettings` strips them from the structured-output schema with a warning (`dropNumericBounds`; TOOL input schemas accept them and are left alone) and honours enum; Gemini / Vertex AI read only the description and drop all three. `enum` is the portable one
- Gemini errors are repackaged as OpenAI exceptions via `repackAsOpenAIException` for adapter compatibility
- `createChatFunCompletion` (the deprecated legacy `functions` API) sends the bare function objects - the tools' `{"type": "function", "function": ...}` wrapper is a 400 ("Missing required parameter: 'functions[0].name'", live 2026-09-30); anything but a `FunctionTool` fails the returned Future
