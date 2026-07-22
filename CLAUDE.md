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
- **anthropic-client** (`anthropic_client`): Anthropic/Claude API client with OpenAI-compatible adapter. Supports MCP toolsets, extended thinking, fast mode, tools (bash, code execution, computer use, web search/fetch, text editor, memory). Also supports x-api-key, static bearer/OAuth token (`ANTHROPIC_AUTH_TOKEN` → `CLAUDE_CODE_OAUTH_TOKEN_ALTERNATIVE` → `CLAUDE_CODE_OAUTH_TOKEN`; the `_ALTERNATIVE` var is safe to export persistently since the real `claude` CLI never reads it), and `ant auth` OAuth profiles with auto-refresh (`forAuthToken` / `forOAuthProfile` / `forAuthTokenProvider` / `customInstance`).
- **google-vertexai-client** (`google_vertexai_client`): Google Vertex AI client (Gemini models on GCP). Supports tools (function declarations, Google search, code execution) with ToolConfig.
- **google-gemini-client** (`google_gemini_client`): Google Gemini API client (direct Gemini API). Supports tools, prompt caching, thinking levels, and has its own exception hierarchy (GeminiScalaClientException) with error code handling.
- **perplexity-sonar-client** (`perplexity_sonar_client`): Perplexity Sonar search-based AI client.
- **claude-agent-client** (`claude_agent_client`): Subprocess transport wrapping the `claude` CLI (Claude Agent SDK-compatible NDJSON protocol over stdin/stdout) - full bidirectional sessions with tool-permission callbacks and interrupt support, distinct from the HTTP-based `anthropic-client`. Requires the `claude` CLI installed and authenticated separately (API key or Claude subscription).

All provider clients depend on openai-core and provide `asOpenAI()` adapters to work with the standard OpenAI interfaces, with one exception: `claude-agent-client` is a fundamentally different subprocess/NDJSON transport (not an `OpenAIChatCompletionService`) and does NOT provide an `asOpenAI()` adapter.

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
    └── perplexity-sonar-client (aggregates core + client + client-stream)

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
- Returns `Source[String, _]` for SSE streams
- Requires Akka Streams materializer in implicit scope

### Model Parameter Conversions
`ChatCompletionSettingsConversions` (in openai-core) automatically adjusts unsupported parameters per model:
- **GPT-5.4**: `max_tokens→max_completion_tokens`, `logprobs` always unsupported; `temperature`, `top_p`, `presence_penalty`, `frequency_penalty` restricted only when `reasoning_effort` is active
- **GPT-5.1/5.2**: Same as GPT-5.4 but `presence_penalty` and `frequency_penalty` always restricted
- **GPT-5.3**: All sampling params always restricted (temperature=1, top_p=1, penalties=0, no logprobs)
- **GPT-5**: Same as GPT-5.3
- **O-series**: `max_tokens→max_completion_tokens`, temperature=1, top_p=1, penalties=0, no parallel tool calls, verbosity medium only
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

Since the ws-client 1.0 engine-discovery migration (`io.cequence:ws-client-*:1.0.0`, a real
Maven Central release since 2026-07 - no longer a SNAPSHOT, resolves in CI with no local-ivy
dependency), EACH service created via the plain factories owns a dedicated ActorSystem (created
eagerly, ~10 threads; its threads are daemon so a leaked service cannot block JVM exit).
`service.close()` terminates both the HTTP client and that system. Build services ONCE and
share the service instance - do NOT construct services per request. A caller-supplied
Materializer is no longer accepted (or needed) by the factories. Timeouts are client-level:
they ride in `TransportSettings` (factories take `timeouts: Option[Timeouts]`), NOT in
`WsRequestContext` (which since ws-client 1.0 carries only per-request data: authHeaders,
extraParams).

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
- When working with structured/JSON output, use `JsonSchema` and `JsonSchemaDef` for type-safe schemas
- Gemini errors are repackaged as OpenAI exceptions via `repackAsOpenAIException` for adapter compatibility
