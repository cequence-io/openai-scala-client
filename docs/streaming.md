# Streaming 🌊

[← back to the README](../README.md)

The stream type every streamed call returns (`AsyncStream`), consuming it from Akka, Pekko, fs2 or ZIO, OpenAI-shaped
streams, the provider-neutral typed stream (`ChatChunk`), the stream frame cap, and human approval of tool calls
mid-stream.

## AsyncStream

🔥 Since 2.0.0 every streamed call returns an `AsyncStream[A]` where 1.x returned an Akka `Source[A, NotUsed]` - chat
chunks, typed `ChatChunk`s, Responses API events, Agents API session events, downloaded bytes. `AsyncStream` comes from
ws-client 1.2.0 (package `io.cequence.wsclient.stream`, in `ws-client-core`): it needs no Akka, no actor system and no
`Materializer` - the run methods take an implicit `ExecutionContext` and return a `Future`. Coming from 1.x, see
[Migrating to 2.0](migration-2.0.md).

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.stream.AsyncStream

import scala.concurrent.{ExecutionContext, Future}

implicit val ec: ExecutionContext = ExecutionContext.global

val service = OpenAIServiceFactory.withStreaming()

val chunks: AsyncStream[ChatChunk] = service.createChatCompletionStreamedTyped(
  Seq(UserMessage("Name three Norwegian fjords.")),
  CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
) // a plan: nothing is sent yet

val printed: Future[Unit] = chunks.texts.runForeach(print) // the request goes out now
```

### Cold: one run, one request

- An `AsyncStream` is an immutable plan. Building it - calling the service method, adding operators - does no I/O;
  the request goes out when the stream is run, and **every run is a new request**, so a re-run is a new, billed call. A
  stream that is never run sends nothing. The work a stream does before its first element runs per run as well: the
  guardrails' input check, Gemini's context caching, the Managed Agents session of a turn, the signature of a Bedrock
  request.
- So do everything in one run: `chunks.runForeach(println)` followed by `chunks.assembled` calls the model twice. To
  print the chunks and assemble the reply, do both in one run:

```scala
import io.cequence.openaiscala.domain.response.AssembledChatCompletion

val assembled: Future[AssembledChatCompletion] =
  chunks
    .map { chunk =>
      println(chunk)
      chunk
    }
    .assembled
```

- A run has one consumer and is back-pressured: elements are pulled, and every buffer is bounded. Fan-out to several
  consumers goes through ws-client's hot `AsyncBroadcastHub`.
- Two exceptions to cold:
  - **Downloads** - `createAudioSpeech`, `retrieveFileContentAsSource`, `getAgentSessionArtifactContent`, Anthropic's
    `downloadFile` and Perplexity's `downloadAgentResponseFile` return a `Future` of the body (of an `Option` of it
    where a 404 is `None`). The request goes out when the method is called, the future completes once the body
    started, and the body stream is **single-run**: run it once, or cancel it - an abandoned one holds its
    connection. `runToBytes()` collects it (at most 256 MiB by default):

```scala
import java.nio.file.{Files, Path, Paths}

val saved: Future[Path] =
  service.createAudioSpeech("Hello from Oslo.").flatMap { audio => // the request goes out here
    audio.runToBytes().map(bytes => Files.write(Paths.get("hello.mp3"), bytes.toArray))
  }
```

  - **The Claude agent's `events`** ([Claude Agent client](claude-agent-client.md)) are hot: the session's CLI output,
    shared by every run. A run starts with the replayed `system/init` message and continues with the events from the
    moment it started - start it before sending a turn whose events you need. The slowest running consumer paces the
    others; with none, the events are read and dropped.

### Operators and runs

The names as in ws-client's `AsyncStream`:

- **Transforming:** `map`, `mapConcat`, `filter`, `filterNot`, `collect`, `evalMap` (one `Future` at a time),
  `stateful` / `statefulMapConcat` (state per run), `scan`, `fold`, `zipWithIndex`, `take`, `takeWhile(p, inclusive)`,
  `drop`.
- **Composing:** `concat` / `++`, `flatMapConcat`, `via(pipe)`, `zipVia(project, pipe)(combine)`.
- **Errors and lifecycle:** `mapError`, `recover`, `recoverWith`; `onTerminate` / `guaranteeCase` (called once per run
  with `Termination.Completed`, `Failed(e)` or `Cancelled`); `concurrently(task, awaitCompletion)` (a side task per
  run); `preflight` (starts a run now, to fail early).
- **Running** (each takes an implicit `ExecutionContext`): `runForeach`, `runFold`, `runToSeq`, `runHead` /
  `runHeadOption`, `runLast` / `runLastOption`, `runDrain` return a `Future`; `run(f)` returns a cancellable
  `StreamRun`, `toPublisher` a `Flow.Publisher`. `describe` shows the plan, never the elements.
- **Building a stream:** `AsyncStream.empty`, `single`, `apply`, `fromIterable`, `fromIterator`, `failed`, `defer`,
  `future`, `futureStream`, `fromPublisher` (a publisher factory, called per run; `fromPublisherOnce` wraps one existing
  publisher - a single-run stream), `fromInputStream`, `fromBlockingIterator`, `fromNative`.
- **Not available:** `merge`, `wireTap` / `alsoTo`, `mapAsync` with a parallelism above 1 (`evalMap` is `mapAsync(1)`),
  `watchTermination` (`onTerminate` takes its place). Convert the stream to your streaming library for those (next
  section).
- **This library adds** on a typed stream `texts`, `thinkingTexts`, `toolCalls`, `toolResults`, `citations`,
  `codeExecutions`, `webSearches`, `images`, `toolApprovalRequests` and `assembled` (`ChatChunkStreamOps`, found without
  an import), and ws-client on a byte stream `runToBytes(maxBytes)`.

The full reference - semantics, operators, framing, `AsyncBroadcastHub` - is ws-client's
[Streaming](https://github.com/cequence-io/ws-client/blob/main/docs/streaming.md) page.

### Cancellation and errors

- **Cancellation** travels to where the elements come from: it aborts the HTTP request (or closes Vertex AI's gRPC
  stream). `take(n)`, `takeWhile` and `runHead` cancel once they have what they need; `run(f)` returns a handle:

```scala
val run = chunks.texts.run(print)

run.cancel() // e.g. the user pressed Stop - the HTTP request is aborted
run.done     // a Future[Unit], completed once the run ended
```

- **Failures** fail the stream: the run's `Future`, or the converted Akka / Pekko / fs2 / ZIO stream. A non-2xx answer
  fails it with the provider's classified exception, the type its non-streamed calls raise (`OpenAIScalaRateLimitException`,
  `OpenAIScalaUnauthorizedException`, ...; `AnthropicScala*`, `GeminiScala*`, `PerplexityScala*` on the native services,
  repacked as `OpenAIScala*` by the `asOpenAI()` adapters), so `Retryable` treats both paths alike. An `{"error": ...}`
  frame inside a 200 stream (e.g. a mid-stream overload) is classified the same way, by the status it names. An
  exception thrown by your own function (in `map`, `evalMap`, ...) fails the stream with that exception; there is no
  resume. SSE keep-alive comments and blank frames are skipped.
- **The frame cap:** one frame - a server-sent event or a JSON line - may take up to 32 MiB (a generated image in base64
  arrives in one event). A larger one fails the stream with an `OpenAIScalaClientException` naming the config key
  `openai-scala-client.streaming.maxFrameLength` and the env. variable `OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH`,
  which raise it (e.g. `128 MiB`; see the [FAQ](faq.md)).

### Native round trips: when a bridge happens

A stream is a plan - where the elements come from (an HTTP response, a collection, a future, a blocking iterator) plus
operators - and the library that runs it compiles the whole plan for itself. Each engine's responses are native to one
library:

| Engine | Artifact | Its responses are natively | 0 bridges when run | 1 bridge when run |
|---|---|---|---|---|
| `jdk` (the default) | `ws-client-jdk` (comes with openai-core) | a `java.util.concurrent.Flow.Publisher` | with the run methods (`runForeach`, ...) | as anything else |
| `play-akka-stream` | `ws-client-play-akka-stream` | an Akka `Source` | as an Akka `Source` (`.toSource`) | as anything else |
| `play-pekko-stream`, `pekko-http` | `ws-client-play-pekko-stream`, `ws-client-pekko-http` | a Pekko `Source` | as a Pekko `Source` (`.toSource`) | as anything else |
| `zio-http` | `ws-client-zio-http` | a ZIO `ZStream` | as a `ZStream` (`.toZStream`, the [ZIO services](zio.md)) | as anything else |

- What this library does to a stream - parsing, the typed chunk mapping, the tool approval rounds, Anthropic's
  `pause_turn` continuation, the guardrails, Bedrock's request signing - consists of operators and per-run steps that
  every consumer runs natively. On the Akka engine, `service.createChatToolCompletionStreamed(...).toSource` is that
  engine's own `Source` with every step an Akka stage, and the same holds for the Pekko engines with `.toSource` and for
  `zio-http` with `.toZStream`. So pick the engine that matches your consumer: adding its artifact selects it over
  `jdk` - see [Choosing an engine](http-engine.md#choosing-an-engine). A download's body stays native on those engines
  too.
- A bridge is built only where two libraries meet: once at a foreign source (a `jdk` response run as an Akka `Source`,
  an Akka engine's response run with `runForeach` or as an fs2 stream), twice around a foreign native pipe (into its
  library and back). fs2 has no engine of its own, so a response crosses one bridge into fs2. The Claude agent's
  `events` are fanned out by ws-client's own interpreter (the one behind the run methods), so an Akka, Pekko, fs2 or
  ZIO consumer of them crosses one bridge there. Sources this library builds itself - Vertex AI's gRPC stream (a
  blocking iterator, read on a dedicated pool), collections, futures - compile natively for every consumer. The
  `Materializer`, `Fs2Runner` or `Runtime` a conversion into an `AsyncStream` takes is used only when the stream is
  consumed outside its own library.
- **What a bridge costs:** it is a Reactive Streams hand-off through `java.util.concurrent.Flow.Publisher` - back-pressure,
  cancellation and errors pass through, but every element crosses an asynchronous boundary between the two libraries'
  threads, with a small bounded buffer in between. The cost is per element and per response, not per operator: the
  operators after the bridge run natively in the consuming library. `StreamDiagnostics.counted(...)` (ws-client) counts
  the bridges a stream builds, e.g. in a test.

### Pipes, and native Flows as parameters

- An `AsyncPipe[A, B]` is a reusable `AsyncStream[A] => AsyncStream[B]`, built from `AsyncPipe.of[A]` and the stream
  operators (or `AsyncPipe.fromFunction(f)`, `AsyncPipe.identity`), applied with `stream.via(pipe)` and chained with
  `pipe.andThen(next)`. Applying a pipe extends the plan, so it runs natively wherever the stream runs. This library's
  own transformations are pipes: `ChatChunks.fromOpenAIChunks`, `ChatChunk.assembleSink` (the fold behind `assembled`:
  `stream.via(ChatChunk.assembleSink)` is a one-element stream, to run in your own library),
  `AgentSessionEvents.untilSettled()`.
- The contract: per-run state lives inside `stateful` / `statefulMapConcat` / `AsyncStream.defer` - a value captured
  when the pipe is built is shared by every run. A pipe that must be 1:1 (the output conversion adapter's
  `messageConversion`) emits exactly one output per input, in order: one that emits more fails the stream, one that drops
  elements misaligns them. A pipe turned into an Akka / Pekko `Flow` (`pipe.toFlow`) may read its input only once.
- Where the library takes a pipe - `OpenAIChatCompletionIOConversionAdapter`'s `outputChunkMessageConversion` /
  `outputChatChunkConversion`, `OpenAIChatCompletionStreamedOutputConversionAdapter`'s `messageConversion` /
  `chunkConversion` - pass a neutral pipe:

```scala
import io.cequence.openaiscala.service.OpenAIChatCompletionIOConversionAdapter
import io.cequence.wsclient.stream.AsyncPipe

val upperCaseText: AsyncPipe[ChatChunk, ChatChunk] = AsyncPipe.of[ChatChunk].map {
  case ChatChunk.Text(text) => ChatChunk.Text(text.toUpperCase)
  case other                => other
}

val shouting = OpenAIChatCompletionIOConversionAdapter(
  service,
  outputChatChunkConversion = Some(upperCaseText)
)
```

  or your library's own transformation: an Akka / Pekko `Flow` as `flow.toAsyncPipe`, an fs2 `Pipe` as
  `Fs2Interop.fromFs2Pipe(pipe)`, a ZIO `ZPipeline` as `ZioInterop.fromZPipeline(pipeline)`. Run by its own library it
  is a native stage (an Akka `Flow` on the Akka engine costs no bridge); run by another, it costs two bridges.

## Consuming a stream: Akka, Pekko, fs2, ZIO

🔥 The run methods need only an `ExecutionContext`. To hand a stream to a streaming library, add that library's ws-client
interop artifact - `"io.cequence" %% "<artifact>" % "1.2.0"`, the ws-client version this library depends on (every
ws-client artifact on a classpath must have the same version) - and import one object:

| Library | Artifact | Import | From a stream | Into a stream |
|---|---|---|---|---|
| Akka | `ws-client-core-akka` (comes with the `play-akka-stream` engine) | `io.cequence.wsclient.stream.SourceInterop._` | `.toSource`, `.toByteStringSource`, `pipe.toFlow` | `.toAsyncStream`, `.toAsyncByteStream`, `flow.toAsyncPipe` (an implicit `Materializer`) |
| Pekko | `ws-client-core-pekko` (comes with `play-pekko-stream` / `pekko-http`) | the same: `io.cequence.wsclient.stream.SourceInterop._` | the same | the same |
| fs2 | `ws-client-fs2` | `io.cequence.wsclient.stream.Fs2Interop._` | `.toFs2[F]`, `pipe.toFs2Pipe[F]` (an implicit `Async[F]`) | `.toAsyncStream`, `Fs2Interop.fromFs2Pipe` (an `Async[F]` and an `Fs2Runner[F]`) |
| ZIO | `ws-client-zio` (comes with `zio-http`) | `io.cequence.wsclient.stream.ZioInterop._` | `.toZStream`, `.toZByteStream`, `pipe.toZStreamFunction` | `.toAsyncStream`, `.toAsyncByteStream`, `ZioInterop.fromZPipeline` (an implicit `Runtime`) |
| Reactive Streams | - | - | `.toPublisher` (an implicit `ExecutionContext`) | `AsyncStream.fromPublisher(() => publisher)` |

The Akka artifacts serve Scala 2.12, 2.13 and 3; the Pekko ones 2.13 and 3.3+; fs2 and ZIO 2.12, 2.13 and 3.3+ (the
`zio-http` engine 2.12 and 2.13). In the snippets below, `chunks` is the `AsyncStream[ChatChunk]` from
[AsyncStream](#asyncstream).

The libraries run a stream alike, with small differences (listed on ws-client's
[Streaming](https://github.com/cequence-io/ws-client/blob/main/docs/streaming.md) page). The one most likely to show:
on a failure Akka's and Pekko's stages fail fast, so the failure overtakes the elements a stage still holds (those of a
`flatMapConcat` or `evalMap` in flight, the rest of a `mapConcat` output), where the run methods, fs2 and ZIO deliver
them before the failure.

**Akka** - with the Akka engine (`ws-client-play-akka-stream`) on the classpath, `toSource` returns the engine's own
`Source`, no bridge:

```scala
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import io.cequence.wsclient.stream.SourceInterop._

implicit val system: ActorSystem = ActorSystem()

chunks.toSource.runWith(Sink.foreach(println))
```

1.x code that treats a stream as a `Source` (`.runWith(...)`, `.via(akkaFlow)`, a `Source`-typed value) compiles
unchanged with `import io.cequence.wsclient.stream.SourceConversions._` - opt-in implicit conversions; on the Akka engine
still without a bridge. That includes `Some(flow)` written where the library takes an `Option` of a pipe; an
`Option[Flow]` held in a value does not convert - map it: `flowOption.map(_.toAsyncPipe)`. See
[Migrating to 2.0](migration-2.0.md) and [Embedding in an Akka, Pekko or ZIO application](http-engine.md#embedding-in-an-akka-pekko-or-zio-application).

**Pekko** - the same object and method names, only your own imports change. Never put the Akka and the Pekko
artifacts on one classpath: ws-client logs a warning naming the jars, and with `ws-client.strict-classpath = true` engine
resolution fails instead.

```scala
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import io.cequence.wsclient.stream.SourceInterop._

implicit val system: ActorSystem = ActorSystem()

chunks.toSource.runWith(Sink.foreach(println))
```

**fs2** - a response crosses one bridge into fs2 (no engine is native to it):

```scala
import cats.effect.IO
import io.cequence.wsclient.stream.Fs2Interop._

val printedWithFs2: IO[Unit] =
  chunks.texts.toFs2[IO].evalMap(text => IO(print(text))).compile.drain
```

An fs2 stream turned into an `AsyncStream` (`.toAsyncStream`) needs an `Fs2Runner[F]` as well: for `IO`, `Fs2Runner.io`
is found once an `IORuntime` is implicit (e.g. `cats.effect.unsafe.implicits.global`).

**ZIO** - on the `zio-http` engine (`ws-client-zio-http`) `toZStream` returns the engine's own `ZStream`, no bridge:

```scala
import io.cequence.wsclient.stream.ZioInterop._
import zio.stream.ZStream

val zChunks: ZStream[Any, Throwable, ChatChunk] = chunks.toZStream

val printedWithZio = zChunks.runForeach(chunk => zio.Console.printLine(chunk))
```

For services typed in ZIO's own terms - `Task` and `ZStream` in place of `Future` and `AsyncStream`, generated for every
service, with `ZLayer` constructors - see [ZIO services](zio.md).

**Anything Reactive Streams** - `toPublisher` turns a stream into a JDK `Flow.Publisher`, each subscription an
independent run (a new request); `org.reactivestreams.FlowAdapters` adapts it to the `org.reactivestreams` interfaces:

```scala
import java.util.concurrent.Flow

val publisher: Flow.Publisher[ChatChunk] = chunks.toPublisher
```

## Chat streams

- **Typed streaming** - `createChatToolCompletionStreamed` (and the tool-less alias `createChatCompletionStreamedTyped`)
  returns an `AsyncStream[ChatChunk]` of a provider-neutral sealed hierarchy that OpenAI (and every OpenAI-compatible
  provider), Anthropic (direct and Bedrock) and Google Gemini / Vertex AI all map onto, so the same pattern match works
  everywhere:

  | `ChatChunk` | Meaning | OpenAI / compatible | Anthropic | Gemini / Vertex AI |
  |---|---|---|---|---|
  | `Start(id, model)` | first chunk | chunk id / model | `message_start` | `modelVersion` |
  | `Text(text)` | answer fragment | `delta.content` | `text_delta` | text part |
  | `Thinking(text)` | reasoning fragment | `delta.reasoning_content` (DeepSeek, Grok, ...) / `delta.reasoning` (Groq) | `thinking_delta` (summarized thinking is requested automatically; on the OpenAI-shaped `createChatCompletionStreamed` it arrives as `delta.reasoning_content` since 1.5.0) | thought-summary part (`includeThoughts` on by default) |
  | `ThinkingSignature(signature, callId)`, `RedactedThinking` | opaque data to echo back in tool loops (`callId` set when the signature rides on a function call) | encrypted reasoning (Responses API) | `signature_delta`, `redacted_thinking` | `thoughtSignature` |
  | `ToolCallStart` / `ToolCallDelta` / `ToolCall` | tool call: start, argument fragments, assembled call | `delta.tool_calls` fragments | `tool_use` + `input_json_delta`; `server_tool_use` / `mcp_tool_use` (`serverSide = true`, MCP servers via `setAnthropicMcpServers`) | `functionCall` (complete); `executableCode` and `mcpServers` calls (`serverSide = true`) |
  | `ToolResult` | result of a provider-executed tool | code interpreter outputs, MCP / file search results (Responses API) | web search / web fetch / code execution / bash / text editor / MCP result blocks | `codeExecutionResult`; `functionResponse` of `mcpServers` calls |
  | `CodeExecution` / `CodeExecutionResult` | semantic view of server-side code runs (emitted in addition to the tool layer, same `callId`) | `code_interpreter_call` (Responses API) | `code_execution` / `bash_code_execution` | `executableCode` / `codeExecutionResult` |
  | `WebSearch` / `WebSearchResult` | semantic view of server-side web searches | `web_search_call` (Responses API) | `web_search` server tool | Google Search grounding queries / chunks |
  | `Image` | generated / returned images (base64 or URL) | image generation partial & final images, code interpreter image outputs (Responses API) | - | inline image parts |
  | `Refusal` | refusal text | `refusal` deltas (Responses API) | - | - |
  | `Citation` | citation / grounding reference | output-text annotations (Responses API) | `citations_delta` | `groundingMetadata` |
  | `ToolApprovalRequest(requestId, toolName, arguments, serverName, runId)` | a tool call the run waits to have approved (see **Human approval** below) | `mcp_approval_request` (Responses API) | Managed Agents tool with an `always_ask` policy | - |
  | `Finish(reason, providerReason)` | normalized stop reason (`stop`, `tool_calls`, `length`, `content_filter`, `approval_required`, `unknown`) + the provider's own | `finish_reason` | `message_delta.stop_reason` | `finishReason` |
  | `Usage(usage)` | OpenAI-shaped usage | trailing usage chunk (`stream_options.include_usage` is requested unless you set `stream_options` in `extra_params`) | merged `message_start` + `message_delta` usage | `usageMetadata` |
  | `Other(kind, raw)` | anything unmodeled (never dropped) | further choices | `ping`, unknown events / blocks | further candidates, unknown parts |

```scala
  import io.cequence.openaiscala.domain.response.ChatChunk._

  val weather = FunctionTool(name = "get_weather", parameters = JsonSchema.Object(
    properties = Seq("location" -> JsonSchema.String()), required = Seq("location")))

  service
    .createChatToolCompletionStreamed(
      messages = Seq(UserMessage("What is the weather in Oslo? Use get_weather.")),
      tools = Seq(weather),
      settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_5, reasoning_effort = Some(ReasoningEffort.medium))
    )
    .runForeach {
      case Thinking(t)                       => print(s"[thinking] $t")
      case Text(t)                           => print(t)
      case ToolCall(_, id, name, args, _)    => println(s"call $name($args) -> $id")
      case ToolResult(_, name, _, text, _)   => println(s"$name returned ${text.getOrElse("")}")
      case Finish(reason, _)                 => println(s"done: $reason")
      case _                                 => ()
    }
```

  The OpenAI-shaped `createChatCompletionStreamed` of the Responses, Agents and full streamed services is rendered from this
  typed stream, and it omits provider-executed activity: server-side tool calls (hosted MCP, multi-agent delegation, hosted
  shell / code interpreter / web search) with their argument fragments, and tool results, never appear as `delta.tool_calls`, because every entry there is a call the client has to run and answer. Only the typed stream carries them; a client function
  call next to a server-side one is kept.

  `stream.texts` is the legacy text-only view (`thinkingTexts`, `toolCalls`, `toolResults`, `citations`, `codeExecutions`,
  `webSearches`, `images` likewise), and `stream.assembled` runs the stream and folds it into a
  `Future[AssembledChatCompletion]` (`ChatChunk.assembleSink` is the same fold as a pipe) whose
  `toAssistantToolMessage` is the assistant turn of a tool loop - append it plus one `ToolMessage` per `clientToolCalls` entry,
  then call again. Every chunk also has a JSON `Format` (`"type"`-keyed) for logging and replay.

  Provider-native server-side tools ride along via `setAnthropicTools` / `setGeminiTools` / `setVertexAITools` /
  `setResponsesTools` and come back as `ToolResult`s; `setGeminiIncludeThoughts(false)` and `setVertexAIIncludeThoughts(false)`
  turn thought summaries off. Anthropic's **MCP connector** rides along the same way: `setAnthropicMcpServers(Seq(MCPServerURLDefinition(name, url)))`
  puts remote MCP servers on the request (typed stream and plain calls alike), their `mcp_tool_use` / `mcp_tool_result` arrive as
  server-side `ToolCall`s / `ToolResult`s, and a `pause_turn` (a tool run that outlived the turn budget) is continued transparently
  on the same stream - one `Start`, one final `Finish`, one summed `Usage`; `setAnthropicMaxContinuations` caps it (default 6). See
  [AnthropicCreateChatToolCompletionStreamedWithMCPServers](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicCreateChatToolCompletionStreamedWithMCPServers.scala).
  (Anthropic API only - Bedrock rejects `mcp_servers`.)

  **Provider-neutral MCP servers and skills** - instead of the provider-specific settings above, pass
  `ChatCompletionTool.MCPServerTool` / `ChatCompletionTool.SkillTool` in `tools` next to your function tools, on
  `createChatToolCompletion` and `createChatToolCompletionStreamed` alike; each adapter maps them onto its native feature or
  fails loudly rather than dropping them:

  | | OpenAI | Anthropic | Gemini | Vertex AI |
  |---|---|---|---|---|
  | `MCPServerTool(name, url, authorizationToken, headers, allowedTools, description, timeout, requireApproval)` | Responses `mcp` tool (the request is routed through the Responses API on any model; approval never required unless asked) | MCP connector `mcp_servers` (bearer token + allowed tools; custom `headers` are refused) | `mcpServers` (bearer as an `Authorization` header, `timeout`; `allowedTools` warned and ignored; Gemini cannot combine it with ANY other tool type, incl. function tools - the adapter fails fast) | refused |
  | `SkillTool(skillId, version, source)` | hosted `shell` tool, `container_auto` environment with `skill_reference`s (GPT-6) | `container.skills` (`Provider` = Anthropic's built-in skills, `Custom` = uploaded; the code execution tool is added) | refused | refused |

```scala
  import io.cequence.openaiscala.domain.ChatCompletionTool.{MCPServerTool, SkillTool, SkillSource}

  service
    .createChatToolCompletionStreamed(
      messages = Seq(UserMessage("What is the 'given' keyword for in Scala 3? Check scala/scala3.")),
      tools = Seq(
        MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp"),          // the provider calls it
        SkillTool("pptx", version = Some("latest"), source = SkillSource.Provider) // Anthropic's built-in skill
      ),
      settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_5)
    )
    .texts
    .runForeach(print) // a stream is cold: the request goes out when it is run
```
  See [CreateChatToolCompletionStreamedWithMCPServerTool](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithMCPServerTool.scala)
  (one DeepWiki server on OpenAI, Anthropic and Gemini) and
  [AnthropicCreateChatToolCompletionWithSkillTool](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/skills/AnthropicCreateChatToolCompletionWithSkillTool.scala).
  Gemini's native MCP (`setGeminiTools(Seq(Tool.McpServers(...)))`, live-verified 2026-09-16 with Exa, the GitHub Copilot MCP and
  DeepWiki, alone and several per request, authenticated with `x-api-key` or `Authorization: Bearer` transport headers alike): Gemini
  runs the tools itself; Gemini 2.5 streams each call as a server-side `ToolCall` (named `<server>_<tool>`, at times by the bare tool
  name - with MCP servers configured every call that is not one of your declared function tools counts as theirs) plus a `ToolResult`,
  Gemini 3 echoes no call / result parts at all. Its executor fails transiently (HTTP 500 / 503, or a stream that ends right after the call) -
  the adapter surfaces that as a `ToolResult(isError = true)` on the stream and as a `GeminiScalaMcpCallNotExecutedException` (a
  server-error subtype, so `Retryable` through the adapter - the retry adapter re-issues it) on the plain call instead of an empty answer. See
  [GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/googlegemini/GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers.scala).

  GPT-5.6, GPT-6 Sol / Luna and GPT-6.1 Terra accept function tools on the chat completions API only with
  `reasoning_effort = none`, and GPT-6 Astra and GPT-6.1 Sol only on the Responses API - so the full `OpenAIServiceFactory()` / `.withStreaming()` service routes their
  tool calls and typed tool streams through the Responses API automatically, which keeps the requested reasoning (only an explicit
  `reasoning_effort = none` stays on chat completions). A chat-only service forces `none` instead (Astra / 6.1 Sol fail fast).
  The same routing serves `settings.setResponsesReasoningMode(ReasoningMode.pro)` (GPT-6 pro mode),
  `settings.setResponsesMultiAgent()` (multi-agent execution) and `service_tier = ServiceTier.ultrafast` (the Ultrafast
  tier, which only the Responses API serves) on every chat entry point. Grok, Groq, Cerebras, Fireworks and DeepSeek use the generic mapping (Groq's per-chunk `usage` is
  emitted once); Sonar and Managed Agents stream text / reasoning / finish / usage but reject tools.

  **Responses API streaming** - `createModelResponseStreamed(inputs, settings)` on the streamed OpenAI service returns
  `AsyncStream[ResponseStreamEvent]` with every server-sent event typed (`ResponseCreated`, `OutputItemAdded/Done`,
  `OutputTextDelta`, `ReasoningSummaryTextDelta`, `FunctionCallArgumentsDelta/Done`, `CodeInterpreterCodeDelta/Done`,
  `ImageGenerationPartialImage`, `OutputTextAnnotationAdded`, `ResponseCompleted/Incomplete/Failed`, `ToolCallStatus` for the
  lifecycle notifications, `UnknownEvent` for the rest), and `createModelResponseStreamedTyped` renders the same stream as `ChatChunk`s -
  reasoning summaries as `Thinking`, the encrypted reasoning as `ThinkingSignature`, web search / code interpreter / MCP / file search /
  image generation as tool-layer chunks plus `WebSearch`, `CodeExecution`, `CodeExecutionResult`, `Image`, annotations as `Citation`.
  A `response.failed` or `error` event fails the stream. See
  [CreateModelResponseStreamed](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/responsesapi/CreateModelResponseStreamed.scala).

  The chat-completion-shaped way to use it - the same messages + `CreateChatCompletionSettings` inputs as
  `createChatToolCompletionStreamed`, exactly like the Anthropic and Gemini `asOpenAI()` adapters expose natively - is available on the
  streamed OpenAI service via `OpenAIStreamedServiceImplicits._`:

```scala
  import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._

  val service = OpenAIServiceFactory.withStreaming()

  service
    .createChatToolCompletionStreamedViaResponses(
      messages = Seq(UserMessage("Search the news about Jupiter missions, then call get_weather for Oslo.")),
      tools = Seq(weather),
      settings = CreateChatCompletionSettings(ModelId.gpt_5_5, reasoning_effort = Some(ReasoningEffort.low))
        .setResponsesTools(Seq(WebSearchTool(), CodeInterpreterTool(container = CodeInterpreterContainer.Auto())))
    )
    .runForeach(println)

  // or as a reusable chat-completion service (sync + typed streamed) served by the Responses API
  val chatViaResponses: OpenAIChatCompletionStreamedService = service.responsesAsChatCompletion
```

  Reasoning summaries are requested automatically when `reasoning_effort` is set (`setResponsesReasoningSummary(false)` opts out;
  `setResponsesReasoningSummary()` asks also without an effort, and on sync calls - the reply's `reasoningMessage.text`), and
  `setResponsesTools` adds Responses-native tools (web search, code interpreter, file search, MCP, image generation) whose activity
  arrives as server-side tool-layer chunks plus `WebSearch`, `CodeExecution`, `Image`, .... `service.responsesAsChatCompletion` also
  exists on a plain (non-streaming) `OpenAIService` through `OpenAIChatCompletionExtra._` for the synchronous adapter. See
  [CreateChatToolCompletionStreamedViaResponses](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/responsesapi/CreateChatToolCompletionStreamedViaResponses.scala).
  See [CreateChatToolCompletionStreamed](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamed.scala),
  [AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter.scala),
  and [GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/googlegemini/GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter.scala).

- **Human approval mid-stream** - a run that pauses until a tool call is approved - an OpenAI MCP tool with
  `requireApproval` (Responses API) or an Anthropic Managed Agents tool with an `always_ask` permission policy - ends its
  typed stream with one `ToolApprovalRequest` per pending call and `Finish(approval_required)`. Answer on the next call with
  `settings.setToolApprovalDecisions(Seq(request.approve))` (or `request.deny(reason)`), or let a callback decide and get
  ONE stream from the question to the final answer:

```scala
  val service = OpenAIServiceFactory.withStreaming()

  service
    .createChatToolCompletionStreamedWithApprovals(
      messages = Seq(UserMessage("Using deepwiki on repo cequence-io/openai-scala-client: which ws-client version does it use?")),
      tools = Seq(
        ChatCompletionTool.MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp", requireApproval = true)
      ),
      settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
    ) { request =>
      println(s"approving ${request.toolName}(${request.arguments})")
      Future.successful(request.approve)
    }
    .runForeach(println)
```

  A resumed OpenAI run continues the stored response by id, so reasoning and executed MCP calls carry over and a run may pause
  several times; everything that cannot pause refuses such a tool instead of running it unapproved. See
  [CreateChatToolCompletionStreamedWithApproval](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithApproval.scala)
  (resuming by hand) and
  [CreateChatToolCompletionStreamedWithApprovalCallback](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/CreateChatToolCompletionStreamedWithApprovalCallback.scala).
