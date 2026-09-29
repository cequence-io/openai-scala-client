# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **OpenAI Scala Client** - an async Scala client for OpenAI API and multiple LLM providers. It's a multi-module Scala project that supports Scala 2.12, 2.13, and 3, providing comprehensive coverage of OpenAI endpoints plus adapters for Anthropic, Google (Gemini/Vertex AI), Perplexity, and other LLM providers.

The library is designed to be self-contained with minimal dependencies and uses a Play WS backend for HTTP calls. It's published as `io.cequence:openai-scala-client` on Maven Central.

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

## Module Architecture

The codebase is organized into multiple SBT subprojects with clear dependency relationships:

### Core Modules
- **openai-core** (`core`): Core domain models, JSON formats, service interfaces (OpenAIService, OpenAICoreService, OpenAIChatCompletionService), adapters, retry helpers, and base exception types. This is the foundation that other modules depend on.
- **openai-client** (`client`): Main client implementation with factories (OpenAIServiceFactory, OpenAIChatCompletionServiceFactory) and concrete service implementations. Depends on and aggregates openai-core.
- **openai-client-stream** (`client_stream`): Streaming extensions providing OpenAIStreamedServiceExtra and streaming factories for SSE-based completions. Depends on openai-client.

### Provider-Specific Clients
- **anthropic-client** (`anthropic_client`): Anthropic/Claude API client with OpenAI-compatible adapter. Structured output drops `minimum` / `maximum` from numeric schema properties (Anthropic 400s on them; `enum` is passed through). On Bedrock, structured output rides in `output_config.format` and is accepted ONLY by the Claude 4.5 / 4.6 profiles (haiku-4-5, sonnet-4-5, opus-4-5, sonnet-4-6, opus-4-6; live-verified 2026-09-18 in eu-central-1 and us-east-1): opus-4-7, opus-4-8, sonnet-5, opus-5 and fable/mythos 5.x return 400 `output_config.format: Extra inputs are not permitted` (beta flag or top-level `output_format` make no difference), so their Bedrock ids are deliberately NOT in `models-supporting-json-schema` and the JSON helper uses prompt-based JSON-object mode for them - re-probe before adding them back. Fable 5 exists in the EU only as `global.anthropic.claude-fable-5`. Claude Opus 5.5 (`claude-opus-5-5`, live-verified 2026-09-22 on the Claude API and Bedrock `eu.`/`us.`/`global.` profiles) is adaptive-only like Opus 5 and additionally rejects `thinking.type = disabled` (the adapter never sends it - `reasoning_effort=none` omits thinking) and forced `tool_choice` `any`/`tool` like Fable 5.1 (downgraded to `auto` + a system instruction via `forcedToolChoiceUnsupportedModels`); NOTE `"claude-opus-5"` is a substring of `"claude-opus-5-5"`, so the `contains`-matched model sets must list Opus 5 and 5.5 deliberately. Claude Sonnet 5.5 (`claude-sonnet-5-5`, live-verified 2026-09-29; Bedrock only as `global.anthropic.claude-sonnet-5-5` - `eu.`/`us.` ids are invalid) is adaptive-only like Sonnet 5 (effort low..max, sampling params and `thinking.type = enabled` rejected, 128k output, `json_schema` on the Claude API, `output_config.format` rejected on Bedrock) but, like Opus 5.5, rejects forced `tool_choice` (in `forcedToolChoiceUnsupportedModels`) and `thinking.type = disabled`: its lowest setting is `thinking.type = between_tools` (no up-front thinking, only progress updates between tool calls; effort high or below; NO other field - a `display` is a 400), which the adapter sends for `reasoning_effort = none` (`betweenToolsThinkingModels`) and the typed stream leaves without the `display: summarized` it adds to adaptive / enabled thinking; Opus 5.5 / Sonnet 5 reject `between_tools`. Again `"claude-sonnet-5"` is a substring of `"claude-sonnet-5-5"`, so Sonnet 5.5 inherits Sonnet 5's set memberships - list it deliberately and never add Sonnet 5 to a set Sonnet 5.5 must not be in. Live walkthrough: `examples/anthropic/ClaudeSonnet55SmokeTest` (the Bedrock leg needs the bearer token - `AWS_BEARER_TOKEN_BEDROCK`). Direct-API examples use `ThinkingSettings.adaptiveSummarized`: Sonnet 5 rejects `thinking.type = enabled` ("Use thinking.type.adaptive and output_config.effort"), and the claude-3-x / sonnet-4-20250514 ids are retired (404). Supports MCP toolsets, extended thinking, fast mode, tools (bash, code execution, computer use, web search/fetch, text editor, memory). Also supports x-api-key, static bearer/OAuth token (`ANTHROPIC_AUTH_TOKEN` → `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE` → `CLAUDE_CODE_OAUTH_TOKEN`; the `_ALTERNATIVE` var is safe to export persistently since the real `claude` CLI never reads it), and `ant auth` OAuth profiles with auto-refresh (`forAuthToken` / `forOAuthProfile` / `forAuthTokenProvider` / `customInstance`).
- **google-vertexai-client** (`google_vertexai_client`): Google Vertex AI client (Gemini models on GCP). Supports tools (function declarations, Google search, code execution) with ToolConfig.
- **google-gemini-client** (`google_gemini_client`): Google Gemini API client (direct Gemini API). Supports tools, prompt caching, thinking levels, and has its own exception hierarchy (GeminiScalaClientException) with error code handling.
- **perplexity-sonar-client** (`perplexity_sonar_client`): Perplexity client. `SonarService` carries the **Agent API** (`/v1/agent`, `/v1/models`; domain in `perplexity/domain/agent/`, JSON in `AgentJsonFormats` - request body built by `createAgentRequestBody`, lenient reads with `Unknown` passthrough for output items / stream events) and the retiring Sonar chat completions API (`createChatCompletion(Streamed)` + `asOpenAI`, `@deprecated` - Perplexity ends it 2026-09-27). Agent streams go through `execRawStream` + the module's own `service/impl/ServerSentEvents` decoder (LF and CRLF framing, multi-line data, `[DONE]`, a non-SSE body - e.g. a JSON error answering the request - is surfaced, never dropped), because the framing could not be observed live. Tests: `AgentOpenApiConformanceSpec` validates every written request against the vendored `perplexity-openapi.json` (fetched 2026-09-25; `prompt_cache_key` / `service_tier` are documented in the guides but missing from the spec) and decodes a generated minimal + maximal instance of every output item / stream event schema plus the documented response examples (`agent-docs-examples/`); `SonarAgentWireSpec` runs the real engine against a local HTTP server. Errors: `service/PerplexityScalaClientException.scala` (native hierarchy classified in `HandlePerplexityErrorCodes` by status + `error.type` - bodies `{"error":{"message","type","code"}}`, `code` = the HTTP status, so a raw stream's error body is classified by it; `x-request-id` rides along, `PerplexityRetryable`). Live facts (2026-09-25): stream framing is LF with `event:` lines and NO `[DONE]`; in a stream Perplexity's own `search_web` call shows up as a `function_call` output item (not in the final output); annotations are empty - citations are `[n]` markers in the text + `search_results`; a bare `model` works without `max_output_tokens` despite the spec; the per-response endpoints (cancel, files, a 404 retrieve) 429 after a short burst. OpenAI adapter: `SonarServiceFactory.agentAsOpenAI` = core `OpenAIResponsesChatCompletionService` over `impl/PerplexityResponsesServiceImpl` (Perplexity's `/v1/responses` alias of `/v1/agent`; create / retrieve / typed stream, the rest fails as unsupported; Perplexity errors repacked onto `OpenAIScala*` with the native one as the cause) - no dependency on openai-client; presets are NOT reachable through it (use `provider/model` ids); Perplexity answers `json_object` with `{}` so JSON needs `jsonSchemaModels = Seq(model)`; the adapter's plain `createChatCompletion` sends no Responses tools (web search goes via `createChatToolCompletion` / the stream). Core Responses reads were made tolerant for it (unknown output item types skipped, unknown `truncation` -> None). Live: `examples/sonar/PerplexityAgentAsOpenAISmokeTest`, `examples/sonar/PerplexityAgentApiSmokeTest` (needs `PERPLEXITY_API_KEY` or `SONAR_API_KEY`; in the edena vault; ~$0.01 per run).
- **typesafe-client** (`typesafe_client`): TypeSafe AI System One API (`POST /v1/systemone`, `GET /v1/models`) - a decision model, not chat: `TypeSafeService.systemOne(state, questions)` sends named typed questions (`NoulQuestion` / `ChoiceQuestion` / `ScoreQuestion`) and returns typed `Answer`s with calibrated probabilities; unknown answer types arrive as `UnknownAnswer`. No streaming at all (live-verified 2026-09-17: `stream: true` is silently ignored and answered with plain JSON; there is no SSE endpoint - `/v1/systemone/stream` and `/sse` are 404). Limits (live-probed 2026-09-17, docs.typesafe.ai/models): ~32k input tokens (~150k chars) shared by state + questions (32,202 accepted, ~33.7k -> 400 `max_tokens_exceeded` -> `OpenAIScalaTokenCountExceededException`), $0.042 / 1M input tokens, output free, 250k tokens/s and 1,200 req/min. Token counts always come back (`usage.input_tokens` billable, `output_tokens` currently free, growing with the number of questions) and the OpenAI adapter maps them onto `UsageInfo(prompt_tokens, total_tokens, completion_tokens)`. `TypeSafeServiceFactory.asOpenAI()` is an `OpenAIChatCompletionService` for `json_schema` structured output ONLY (`impl/OpenAITypeSafeChatCompletionService` + `impl/SchemaQuestions`: boolean -> noul, string enum -> choice, numeric enum / `minimum`..`maximum` range of at most 32 whole numbers -> score (levels sent as text - the API refuses numeric levels; integer -> most likely value, number -> expected value), array of string enum -> one noul per option, nested objects; messages -> state via the public `TypeSafeChatMapping.toState` (system/developer -> `instructions`, a JSON-object/array user message embedded as JSON, lone user message alone = the state, with instructions -> `{instructions, message}`, several turns -> `{instructions, conversation}`; chosen by `examples/typesafe/TypeSafeMessageMappingBenchmark`: instructions in the state score 8/8 at a flat ~110 tokens vs 7/8 and ~110 tokens PER question when copied into every question); `TypeSafeChatMapping.toQuestions(schema)` previews the questions; `examples/typesafe/TypeSafeOpenAIAdapterScenarios` walks 16 live cases incl. every refusal; answers -> the assistant content as JSON, `SystemOneResponse` in `originalResponse`; noul threshold via `setTypeSafeNoulThreshold`; anything else refused before I/O). **Confidence fields** (`SchemaQuestions`): a `number` property `<base>_confidence` / `<base>Confidence` (case-sensitive suffix, exact sibling name - `isUrgentConfidence` does NOT match `is_urgent`) with a sibling `<base>` in the same object, at any depth, is not asked but filled from the answers, right after its base field, rounded half-up to 4 decimals: boolean -> the probability of the emitted answer (`noul` if it reads true at the threshold, else `1 - noul`), choice / score -> the answer's peakedness `confidence`, multi-select -> the minimum over its options (as booleans), object -> the minimum over every question underneath; both spellings may be declared; a confidence-like property without a sibling (or whose base is itself a confidence field) is planned as usual (and refused as a free number), a non-`number` one is refused; a question without a usable answer leaves the confidence out with a WARN. Works on the typed and the legacy map-form schema alike (the planner reads the schema JSON). Live-verified 2026-09-28 (`TypeSafeOpenAIAdapterScenarios` 7b). Of the standard settings only `model`, `response_format_type` (must be json_schema), `jsonSchema` and `n` = 1 are honoured - every other one that is set (temperature, top_p, stop, max_tokens, penalties, logit_bias, logprobs, top_logprobs, user, seed, store, reasoning_effort, verbosity, service_tier, parallel_tool_calls, metadata, foreign extra_params) is dropped with a single warning naming them (`OpenAITypeSafeChatCompletionService.unsupportedSettings` / `unsupportedSettingsMessage`). `jev-*` are in `models-supporting-json-schema` so `createChatCompletionWithJSON` stays in schema mode - the match is exact-name (or `-<name>` suffix), so EVERY new dated build (`jev-1.14.0`, ...) must be added there alongside its `NonOpenAIModelId` constant. Env: `TYPESAFE_API_KEY`, optional `TYPESAFE_BASE_URL` / `TYPESAFE_DEFAULT_MODEL` (same as the official SDKs). Errors are a native hierarchy (`service/TypeSafeScalaClientException.scala`, classified by status + `detail.error_type` in `HandleTypeSafeErrorCodes.toException`, bodies collected live 2026-09-17: Unauthorized 401/403, TokenCountExceeded 400 max_tokens_exceeded, ApiUsage 400 api_usage_error, InvalidRequest 400 plain-detail / 422 with `violations`, NotFound 404/405, ClientTimeout 408 + transport, RateLimit 429, EngineOverloaded 503/529, ServerError 5xx; each carries httpCode / errorType / requestId via the shared `io.cequence.openaiscala.ProviderErrorDetails` trait, whose `unapply` walks the cause chain so repacked OpenAIScala* exceptions expose them too); `TypeSafeRetryable` classifies, `TypeSafeServiceAdapters.retry` uses it; the OpenAI adapter repacks them via `impl.repackAsOpenAIException` (native as cause). A 429 could not be provoked (800 requests in a burst all 200). Tests pin the wire format against the vendored OpenAPI spec (`src/test/resources/typesafe-openapi.json`, 0.2.0) and the Python SDK's fixtures, plus a local-HTTP-server wire spec. Live-verified 2026-09-16 (`examples/typesafe/TypeSafeSmokeTest`, 11 sections): `jev-latest` -> `jev-1.13.0` in the response, `jev-preview` exists, the dated `jev-1.12` is gone; a noul without instructions/criteria, an empty choice, a choice with more than 255 options (`ChoiceQuestion.MaxOptions`), a numeric score level and a non-text/object/array state are 400/422 on the server and `IllegalArgumentException`s here (the schema planner reports a >255 enum by path); an org-gated `bounding_box` question type exists but is unmodelled (it would arrive as `UnknownAnswer`).
- **claude-agent-client** (`claude_agent_client`): Subprocess transport wrapping the `claude` CLI (Claude Agent SDK-compatible NDJSON protocol over stdin/stdout) - full bidirectional sessions with tool-permission callbacks and interrupt support, distinct from the HTTP-based `anthropic-client`. Requires the `claude` CLI installed and authenticated separately (API key or Claude subscription).

All provider clients depend on openai-core and provide `asOpenAI()` adapters to work with the standard OpenAI interfaces, with two exceptions: `claude-agent-client` is a fundamentally different subprocess/NDJSON transport (not an `OpenAIChatCompletionService`), and `typesafe-client` wraps a decision API whose `asOpenAI()` serves `json_schema` structured output only (no text generation, no streaming).

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
- Streamed SSE frames are capped at 1 MB (ws-client's default of 20 KB broke Anthropic web-search result blocks)
- Requires Akka Streams materializer in implicit scope only when consuming the `Source`

### Model Parameter Conversions
`ChatCompletionSettingsConversions` (in openai-core) automatically adjusts unsupported parameters per model. Every OpenAI rule below was MEASURED on 2026-09-26 against the live chat completions API (41 models x 23 parameter cases, raw and through the client) - re-run `examples/OpenAIConversionsAudit <out.jsonl> <models...>` (and `examples/googlegemini/GeminiThinkingAudit`) when models change; `ModelConversionsRoutingSpec` / `GeminiThinkingSpec` pin the results. "Without reasoning" = no `reasoning_effort` or `none`.
- **Dispatch** (`ChatCompletionBodyMaker`, on the Bedrock-canonical id): o1-preview/o1-mini (`isO1PreviewOrMini`) -> o-series by pattern `o[134](-...)` (`isOSeries`; future-proofing - the ids the old static set missed are Responses-only (o3-pro) or shut down (deep-research)) -> `chat-latest` -> GPT-6 Astra / GPT-6.1+ (`isGpt6ReasoningAlwaysOn`, by the PARSED `gpt6Minor`) -> other `gpt-6-*` (Sol/Luna rules) -> `gpt-5-search-api*` -> GPT-5 by PARSED minor version (`gpt5Minor`: `gpt-5.10` is minor 10, not 1; `gpt-5.4.1-mini` is minor 4; any minor >= 6 gets the 5.6 rules, not the oldest)
- **GPT-6 Sol / Luna** (`gpt6SolLuna` = the GPT-5.6 rules): unlike Astra they accept `reasoning_effort=none` and function tools on chat completions (with `none` only). There is no `gpt-6-terra`; an unknown `gpt-6-*` id gets the Sol/Luna rules
- **GPT-6.1 Sol** (DevDay 2026-09-29, measured 2026-09-29 incl. Bedrock `global.openai.gpt-6.1-sol`): the ASTRA rules (`gpt6`), not Sol's - reasoning always on (`none`/`minimal` rejected on both APIs), function tools Responses-only with every effort. Any `gpt-6.<minor>` with minor >= 1 gets them too (`isGpt6ReasoningAlwaysOn`). Bedrock's chat completions also accept `max` (still sent as `xhigh`) and reject `reasoning.mode` / the `fast` tier
- **Responses-only settings** (`chatRequiresResponsesAPI(settings)`): approval decisions, `setResponsesTools`, `setResponsesReasoningMode` (`reasoning.mode` `standard`/`pro` - GPT-6 only; the chat completions API has no such parameter) and `service_tier = ultrafast` (chat completions 400s it for every model; the Responses API serves it for GPT-6 Astra, access-controlled). The full service routes them to the Responses adapter on every chat entry point (sync, tools, OpenAI-shaped and typed streams - the OpenAI-shaped stream still refuses decisions / Responses-native tools); chat-only services refuse the first three and pass `ultrafast` to the API. `ServiceTier` has `priority` / `fast` (same Fast mode; GPT-6 responses report `fast`) and `ultrafast`; `ReasoningConfig` has `mode` / `context` (tolerant reads - an unknown effort / mode / context in a Response reads as absent)
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
- **Non-reasoning models** (gpt-3.5 / 4 / 4o / 4.1) get no conversion: a `reasoning_effort` / `verbosity` there is left to the API's clear 400
- **Gemini thinking** (`gemini/service/impl/GeminiThinking`): levels for Gemini 3.x, the rolling aliases (`gemini-flash-latest`, `-flash-lite-latest`, `-pro-latest`) and `nano-banana-pro*`; budgets for 2.5; nothing for `gemini-2.5-flash-image`. MINIMAL is rejected by the Pro models (not 3-pro-image), 3.7 / 3.8 Flash and `gemini-flash-latest`; the 3.1 Flash image models accept only MINIMAL / HIGH. `gemini-omni-*` only serve the Interactions API (not usable by the adapter)
- **JSON-schema model matching** (`handleOutputJsonSchema`): exact id, `-<id>` suffix, and for OpenAI-on-Bedrock ids the canonical id (`us.openai.gpt-5.6-luna` matches `gpt-5.6-luna`); a Bedrock Anthropic id never matches its bare Claude name (Bedrock rejects `output_config.format` for 4.7+ / 5.x, and so does the mantle short form `anthropic.claude-haiku-4-5`)
- **Anthropic max-output table**: the LONGEST matching id wins, so the `contains`-matched table's order no longer matters
- **Groq**: DeepSeek R1 models need `max_completion_tokens` and optional reasoning format

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
3. **When adding features**: Add examples in openai-examples module
4. **When changing domain**: Update the appropriate JsonFormats.scala with JSON codecs
5. **When adding provider support**: Create new client module following anthropic-client pattern
6. **When adding new models**: Update `ModelId.scala` (or `NonOpenAIModelId.scala`), add to `models-supporting-json-schema` in config if JSON schema is supported, add parameter conversions in `ChatCompletionSettingsConversions` if needed, and add the model prefix handling in `OpenAIChatCompletionServiceImpl`

## Testing Strategy

- Unit tests use ScalaTest with ScalaMock
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
