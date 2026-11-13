# Migrating to 2.0 🧭

[← back to the README](../README.md)

What changes for your code when you move from 1.x (1.7.0) to 2.0.0, with before / after code for each kind of user.

2.0.0 removes the hard dependency on Akka. Calls go through any [ws-client](https://github.com/cequence-io/ws-client)
1.2.1 engine - by default `jdk` (the JDK's `java.net.http`), which needs no actor system - and every stream is ws-client's
backend-neutral `AsyncStream`, which runs on plain `Future`s or converts to Akka, Pekko, fs2 or ZIO with one import. The
services, their methods, the domain classes, the settings and the factories are the ones of 1.7.0. What changed:

- the stream types (`AsyncStream` / `AsyncPipe` instead of Akka's `Source` / `Flow`), and streams are cold - a request
  per run;
- the default HTTP engine (`jdk` instead of Play WS on Akka);
- no factory or helper takes a `Materializer`, an `ActorSystem` or an Akka `Scheduler`;
- logback is no longer a dependency.

2.0.0 is not binary compatible with 1.x: recompile. The complete list of changes is in the [changelog](../CHANGELOG.md).
The 1.x line (1.7.x) stays on Akka.

## Which part applies to you

| Your code | What to do |
|---|---|
| Uses `Future`s only, no streaming | Add an SLF4J binding ([Logging](#logging)); drop the `ActorSystem` / `Scheduler` you kept for retries and the JSON helpers ([Futures-only code](#futures-only-code)) |
| Consumes streams with Akka | Add the Akka engine and convert with one import - no bridges ([Akka](#akka)) |
| Uses Pekko | Add a Pekko engine and the same import ([Pekko](#pekko)) |
| Uses fs2 | Add `ws-client-fs2` ([fs2](#fs2)) |
| Uses ZIO | Use `openai-scala-zio`, or `ws-client-zio` ([ZIO](#zio)) |
| Runs a stream twice, or builds one and never runs it | [Streams are cold](#streams-are-cold) |
| Implements or wraps a service trait | Return `AsyncStream`s ([Implementing the service traits](#implementing-the-service-traits)) |
| Passes a `Materializer`, `ActorSystem` or `Scheduler` | [The parameters that are gone](#materializer-actorsystem-and-scheduler-parameters) |
| Shares an engine, or depends on Play WS behavior | [The default HTTP engine](#the-default-http-engine) |
| Uses the Claude agent client or the guice module | [Claude agent client](#claude-agent-client), [The guice module](#the-guice-module) |

## Dependencies

```scala
libraryDependencies ++= Seq(
  "io.cequence" %% "openai-scala-client-stream" % "2.0.0", // or openai-scala-client, openai-scala-all, ... as in 1.x
  "ch.qos.logback" % "logback-classic" % "1.4.14"           // an SLF4J binding of your choice - see Logging
)
```

- 1.x brought Akka 2.6, Play WS and logback through its modules; 2.0.0 brings none of them. openai-core depends on
  ws-client-core, ws-client-jdk (the default engine), json-repair and scala-logging.
- Every ws-client artifact on a classpath must have the same version - 1.2.1 for 2.0.0. A build that declares a ws-client
  artifact of its own (e.g. `ws-client-play-akka-stream` 1.1.5) moves it to 1.2.1.
- Scala 2.12, 2.13 and 3, as before. The 2.13 artifacts are built with Scala 2.13.18 (was 2.13.11), and sbt 1.10+ then
  asks for a `scalaVersion` of at least 2.13.18. On Scala 3 the new `openai-scala-zio` and ws-client's Pekko, fs2 and ZIO
  modules need 3.3+; the other modules serve 3.2 and 3.3+. Java 11+.

## Logging

1.x brought logback-classic with openai-core, so log output appeared without any setup. 2.0.0 logs through the SLF4J API
only and brings no backend: add the binding your application uses. Without one, SLF4J prints a warning at startup and
nothing is logged.

```scala
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.14" // the version 1.x brought
```

An application on another backend (log4j 2, slf4j-simple, ...) no longer has to exclude logback. If you add a Play
engine, keep `play.shaded.ahc.org.asynchttpclient.netty.handler` at `INFO` or above: at `DEBUG` it logs complete request
headers, `Authorization` included.

## Futures-only code

Calls that return a `Future` keep their signatures. In 1.x the retry adapter, `RetryHelpers`, the failover and JSON
helpers, the batch helper's polling and the TypeSafe retry adapter took an implicit Akka `Scheduler`, so an application
without any streaming still needed an `ActorSystem`. They take ws-client's `AsyncScheduler` now, whose default (one daemon
thread) is found without an import.

Before (1.7.0):

```scala
import akka.actor.{ActorSystem, Scheduler}

implicit val system: ActorSystem = ActorSystem()
implicit val ec: ExecutionContext = system.dispatcher
implicit val scheduler: Scheduler = system.scheduler // the JSON / failover helpers and the retries needed it

val service = OpenAIServiceFactory()

val answer: Future[JsObject] = service.createChatCompletionWithJSON[JsObject](messages, settings)
```

After (2.0.0):

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIServiceFactory
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future}

implicit val ec: ExecutionContext = ExecutionContext.global

val service = OpenAIServiceFactory() // runs on the jdk engine

val answer: Future[JsObject] =
  service.createChatCompletionWithJSON[JsObject](
    Seq(UserMessage("""What is the capital of Norway? Answer as {"capital": "..."}""")),
    CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
  ) // retries wait on the default AsyncScheduler
```

- A scheduler passed explicitly (`(ec, system.scheduler)`) no longer compiles: pass none, or an
  `io.cequence.wsclient.concurrent.AsyncScheduler`. `AsyncScheduler.fromExecutor(executor)` wraps a
  `ScheduledExecutorService`; in an Akka or Pekko application `import io.cequence.wsclient.concurrent.ActorSchedulers._`
  (ws-client-core-akka / -pekko) makes the implicit actor-system scheduler in scope serve instead - see
  [Adapters](adapters.md).
- An implicit `akka.actor.Scheduler` that is still in scope (without that import) is not used: the default takes over.
- The services now run on the `jdk` engine instead of Play WS - see [The default HTTP engine](#the-default-http-engine).

## Streams are cold

Every streaming method returns an `AsyncStream[A]` (`io.cequence.wsclient.stream`) instead of an Akka
`Source[A, NotUsed]`, every stream transformation the library offers is an `AsyncPipe[A, B]` instead of a `Flow`, and a
download's body is an `AsyncStream[Bytes]` instead of a `Source[ByteString, _]` - see [AsyncStream](streaming.md#asyncstream).

The moment the request is sent changed too. A 1.x stream from the Play engine sent its request when the method was
called. An `AsyncStream` is a plan: it sends nothing until it runs, and every run sends a new request.

| Where | 1.7.0 | 2.0.0 |
|---|---|---|
| The HTTP streams of every service: chat completions (OpenAI-shaped and typed), completions, the Responses API, Agents sessions and events, Anthropic (messages, batch results, session events), Gemini, Perplexity | the request is sent when the method is called | the request is sent when the stream runs - a new request per run |
| Bedrock streams (SigV4) | signed when the method is called | signed when the stream runs, per run |
| The streamed guardrails' input check, Gemini's system-message cache (`enableCacheSystemMessage`), the first request of Anthropic's `pause_turn` continuation | when the method is called | per run |
| The Anthropic Managed Agents adapter's session | created when the method is called - a stream never run left a session behind | created per run |
| The OpenAI Agents adapter, `createChatToolCompletionStreamedWithApprovals`, the Responses adapter's lookups, Vertex AI streams | already per run | unchanged |
| Downloads: `createAudioSpeech`, `retrieveFileContentAsSource`, `getAgentSessionArtifactContent`, Anthropic's `downloadFile`, Perplexity's `downloadAgentResponseFile` | a `Future`: the request is sent at the call | unchanged: the `Future` completes once the body started; the body stream runs once (a second run fails) |
| The Claude agent client's `events` | hot | hot - see [Claude agent client](#claude-agent-client) |

What this means:

- A stream that is built and never run sends nothing: no request, no guardrail check, no Gemini cache, no Managed Agents
  session.
- Running the same stream value twice sends two requests, and both are billed. Keep the elements of one run (`runToSeq`)
  instead, or fan one run out through ws-client's `AsyncBroadcastHub`.
- `stream.assembled` runs the stream. Printing a stream and then assembling it asks the model twice; do both in one run:

```scala
import io.cequence.openaiscala.domain.response.{AssembledChatCompletion, ChatChunk}

import scala.concurrent.Future

// service, messages, settings and an implicit ExecutionContext in scope
val reply: Future[AssembledChatCompletion] =
  service
    .createChatCompletionStreamedTyped(messages, settings) // nothing is sent yet
    .map { chunk =>
      chunk match {
        case ChatChunk.Text(text) => print(text)
        case _                    => ()
      }
      chunk
    }
    .assembled // one run: printed and assembled from the same request
```

- Cancelling a run - a `take`, a failing consumer, `StreamRun.cancel()` - aborts its HTTP request.
- A non-2xx status fails the stream with the provider's classified exception, as in 1.x.

## Akka

With the Akka engine on the classpath, 2.0.0 runs on Play WS and Akka as 1.x did, and hands out Akka `Source`s with one
import - except that streams are cold. Add the engine 1.x used:

```scala
libraryDependencies += "io.cequence" %% "ws-client-play-akka-stream" % "1.2.1" // Scala 2.12, 2.13 and 3
```

The factories pick it on their own (priority 11; `jdk` has 0). Then `import io.cequence.wsclient.stream.SourceInterop._`
adds `.toSource`, which on this engine returns the engine's own `Source` with the library's operators as Akka stages -
no bridge.

Before (1.7.0):

```scala
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

implicit val system: ActorSystem = ActorSystem()
implicit val materializer: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val service = OpenAIServiceFactory.withStreaming()

service
  .createChatCompletionStreamed(messages, settings)
  .runWith(Sink.foreach(chunk => print(chunk.contentHead.getOrElse(""))))
```

After (2.0.0):

```scala
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.stream.SourceInterop._

import scala.concurrent.ExecutionContext

implicit val system: ActorSystem = ActorSystem()
implicit val ec: ExecutionContext = system.dispatcher

val service = OpenAIServiceFactory.withStreaming() // on play-akka-stream, found on the classpath

service
  .createChatCompletionStreamed(
    Seq(UserMessage("Name three Norwegian fjords.")),
    CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
  )
  .toSource // the engine's own Source - no bridge
  .runWith(Sink.foreach(chunk => print(chunk.contentHead.getOrElse(""))))
```

To keep 1.x code unchanged, `import io.cequence.wsclient.stream.SourceConversions._` instead: its opt-in implicit
conversions (`AsyncStream` ↔ `Source`, `AsyncPipe` ↔ `Flow`) make `.runWith(sink)`, `.via(flow)` and assigning a stream to
a `Source[A, NotUsed]` compile as they did. They are `implicit def`s, warning-free under `-feature` on Scala 2 and 3.
Prefer the explicit `.toSource` in new code.

- Where the library takes an `Option` of a pipe, `Some(flow)` written in the call compiles with `SourceConversions._`;
  with `SourceInterop._`, or for an `Option[Flow]` held in a value, convert explicitly: `Some(flow.toAsyncPipe)` /
  `flowOption.map(_.toAsyncPipe)`. Either way the conversion needs an implicit `Materializer` (the implicit
  `ActorSystem` above provides one), and a `Flow` passed this way runs natively on Akka.

```scala
import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.service.OpenAIChatCompletionIOConversionAdapter
import io.cequence.wsclient.stream.SourceInterop._

// service, system and ec as above
val withoutThinking: Flow[ChatChunk, ChatChunk, NotUsed] =
  Flow[ChatChunk].filterNot(_.isInstanceOf[ChatChunk.Thinking])

val adapted = OpenAIChatCompletionIOConversionAdapter(
  service,
  outputChatChunkConversion = Some(withoutThinking.toAsyncPipe) // 1.x: Some(withoutThinking)
)
```

- The typed-stream helpers - `texts`, `toolCalls`, `toolResults`, ... (`ChatChunkStreamOps`, was `ChatChunkSourceOps`) -
  are on `AsyncStream[ChatChunk]`: call them before `.toSource`. `assembled` takes an implicit `ExecutionContext` instead of
  a `Materializer`; `ChatChunk.assembleSink` is an `AsyncPipe` that emits one `AssembledChatCompletion`
  (`source.via(ChatChunk.assembleSink.toFlow).runWith(Sink.head)` on the Akka side).
- Downloads: `.toByteStringSource` turns the body into a `Source[ByteString, NotUsed]` (on the Akka engine the engine's
  own, without a copy):

```scala
import akka.stream.scaladsl.FileIO
import java.nio.file.Paths

service
  .createAudioSpeech("Hello from 2.0.")
  .flatMap(_.toByteStringSource.runWith(FileIO.toPath(Paths.get("hello.mp3"))))
```

- Without the Akka engine - on `jdk` - `.toSource` works the same (it needs `ws-client-core-akka` 1.2.1 on the classpath),
  and each HTTP response crosses one bridge (`java.util.concurrent.Flow.Publisher`) into Akka.
- Embedding the library in an existing `ActorSystem` - an engine on your `Materializer` that closing a service never
  touches - is unchanged: see [Embedding](http-engine.md#embedding-in-an-akka-pekko-or-zio-application).
- On Scala 2.13 the Akka engine runs Play WS 2.2.18 since ws-client 1.2.0, which needs Java 11+.
- Akka and Pekko artifacts must never share a classpath (they deliberately use the same class names): ws-client logs a
  warning, or fails with `ws-client.strict-classpath = true`.

More in [Consuming a stream](streaming.md#consuming-a-stream-akka-pekko-fs2-zio).

## Pekko

1.x could not run on Pekko: every module depended on Akka-flavored ws-client artifacts, whose classes clash with the Pekko
ones. 2.0.0 depends on neither flavor, so a Pekko engine is one dependency (Scala 2.13 or 3.3+):

```scala
libraryDependencies += "io.cequence" %% "ws-client-play-pekko-stream" % "1.2.1" // or ws-client-pekko-http
```

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.stream.SourceInterop._ // from ws-client-core-pekko: the same names, Pekko types
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.ExecutionContext

implicit val system: ActorSystem = ActorSystem()
implicit val ec: ExecutionContext = system.dispatcher

val service = OpenAIServiceFactory.withStreaming() // on play-pekko-stream (priority 21)

service
  .createChatCompletionStreamedTyped(
    Seq(UserMessage("Name three Norwegian fjords.")),
    CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
  )
  .texts
  .toSource // the engine's own Pekko Source - no bridge
  .runWith(Sink.foreach[String](print))
```

`SourceConversions._` and `ActorSchedulers._` exist for Pekko too (`ActorSchedulers` adapts an
`org.apache.pekko.actor.Scheduler`). `pekko-http` ignores `TransportSettings.proxyURL` (with a warning).

## fs2

1.x handed fs2 an Akka `Source`: an `ActorSystem`, a `Materializer` and a Reactive Streams bridge into fs2. In 2.0.0 one
import does it:

```scala
libraryDependencies += "io.cequence" %% "ws-client-fs2" % "1.2.1" // Scala 2.12, 2.13 and 3.3+
```

```scala
import cats.effect.IO
import io.cequence.wsclient.stream.Fs2Interop._

val texts: fs2.Stream[IO, String] =
  service
    .createChatCompletionStreamedTyped(messages, settings)
    .texts
    .toFs2[IO] // each run of the fs2 stream is one request; interrupting it aborts the request
```

- On the default `jdk` engine each HTTP response crosses one bridge into fs2.
- The other way, `fs2Stream.toAsyncStream` needs an implicit `Fs2Runner` - `Fs2Runner.io` from an implicit `IORuntime`
  (e.g. `import cats.effect.unsafe.implicits.global`) - which is used only when the stream is consumed outside fs2.
  Pipes: `pipe.toFs2Pipe[IO]`, `Fs2Interop.fromFs2Pipe(fs2Pipe)`.

## ZIO

The new `openai-scala-zio` module gives every service as ZIO types - `Task`s for calls, `ZStream[Any, Throwable, A]` for
streams, `ZLayer`s that close the service with their scope - see [ZIO services](zio.md).

Before (1.7.0):

```scala
val reply: Task[ChatCompletionResponse] =
  ZIO.fromFuture(implicit ec => service.createChatCompletion(messages, settings))
```

After (2.0.0):

```scala
libraryDependencies += "io.cequence" %% "openai-scala-zio" % "2.0.0" // Scala 2.12, 2.13 and 3.3+
```

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIStreamedServiceZIO}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import zio.{Console, Task, ZIO}

import scala.concurrent.ExecutionContext.Implicits.global

val program: Task[Unit] =
  ZIO
    .serviceWithZIO[OpenAIStreamedServiceZIO](
      _.createChatCompletionStreamedTyped(
        Seq(UserMessage("Name three Norwegian fjords.")),
        CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      ).runForeach(chunk => Console.printLine(chunk))
    )
    .provide(OpenAIStreamedServiceZIO.layer(() => OpenAIServiceFactory.withStreaming()))
```

- With the `zio-http` engine (`ws-client-zio-http`, Scala 2.12 / 2.13) a service stream is the engine's own `ZStream`, with
  the library's operators as ZIO stages - no bridge. On `jdk` each HTTP response crosses one bridge.
- The provider views (`AnthropicServiceZIO`, `GeminiServiceZIO`, ...) need their provider module, which the ZIO module
  does not bring.
- A call is `ZIO.fromFuture` over the service's `Future`: interrupting it does not abort its request.
- Without the module, `import io.cequence.wsclient.stream.ZioInterop._` (ws-client-zio) converts any stream:
  `stream.toZStream`, a download's body with `toZByteStream`.

## Implementing the service traits

Code that implements or wraps a streamed service - `OpenAIChatCompletionStreamedServiceExtra`,
`OpenAIStreamedServiceExtra`, `AnthropicService`, `GeminiService`, `SonarService`, ... - returns `AsyncStream`s from the
streaming methods and `AsyncPipe`s where 1.x took or returned a `Flow`.

- Send nothing when the method is called: build the request inside the stream, so it goes out per run.
  `AsyncStream.defer { ... }` evaluates its block per run; `AsyncStream.future(...)` / `futureStream(...)` take a by-name
  `Future`, evaluated per run.
- Per-run state (counters, builders, clocks) belongs inside `defer`, a `stateful` init or a `statefulMapConcat` factory -
  never in a field or a `val` of the method, or every run shares it.
- To wrap what you have: `AsyncStream.fromPublisher(() => publisher)` (the factory is called once per run); an Akka
  `Source` with `source.toAsyncStream` (`SourceInterop`, it stays native on Akka); `fromBlockingIterator(open, close)` /
  `fromInputStream(() => in)` for blocking sources (read on a dedicated pool); `fromNative(leaf)` for a stream type of
  your own - a `NativeStream` whose `openPublisher()` starts a fresh run per call. Transformations: `AsyncPipe.of[A].map(...)`,
  or `flow.toAsyncPipe`.
- A wrapper delegates `createChatToolCompletionStreamed` explicitly: the trait's default derives the typed stream from
  the OpenAI-shaped one and refuses tools.
- A service on a ws-client engine extends `WSClientWithEngineAsyncStreamingBase` (1.x:
  `WSClientWithEngineOutputStreamingBase`): its `execJsonStream` / `execRawStream` return `AsyncStream`s whose non-2xx
  failures go through your `handleErrorCodes`.

```scala
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionChunkResponse}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtra
import io.cequence.wsclient.stream.AsyncStream

class TimedStreamedService(underlying: OpenAIChatCompletionStreamedServiceExtra)
    extends OpenAIChatCompletionStreamedServiceExtra {

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): AsyncStream[ChatCompletionChunkResponse] =
    timed(underlying.createChatCompletionStreamed(messages, settings))

  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): AsyncStream[ChatChunk] =
    timed(underlying.createChatToolCompletionStreamed(messages, tools, responseToolChoice, settings))

  // the clock starts when a run starts - once per run, not when the method is called
  private def timed[A](stream: AsyncStream[A]): AsyncStream[A] =
    AsyncStream.defer {
      val started = System.nanoTime()
      stream.onTerminate { termination =>
        println(s"$termination after ${(System.nanoTime() - started) / 1000000} ms")
      }
    }

  override def close(): Unit = underlying.close()
}
```

## Materializer, ActorSystem and Scheduler parameters

No factory or helper takes a `Materializer` or an `ActorSystem`; timers take an `AsyncScheduler`, whose default is found
without an import. Code that relied on the implicits compiles unchanged; code that passed them explicitly drops them.

| 1.7.0 | 2.0.0 |
|---|---|
| `agentsAsChatCompletion(...)(implicit ec, materializer)`, `OpenAIAgentsChatCompletionService(...)` | `(implicit ec, scheduler: AsyncScheduler)` |
| `AnthropicServiceFactory.managedAgentAsOpenAI` (both), `managedAgentAsOpenAIWithAuthToken`, `OpenAIAnthropicManagedAgentChatCompletionService(...)`: `(implicit ec, materializer)` | `(implicit ec)` |
| `ClaudeAgentServiceFactory.startSession(settings)(implicit ec, materializer)` | `(implicit ec)` |
| `adapters.parallelTakeFirst(services: _*)(implicit materializer)` | no implicit parameter |
| `chunks.assembled(implicit materializer)` | `(implicit ec)` |
| `RetryHelpers`, the `retry` adapters, `TypeSafeServiceAdapters.retry`, the failover / JSON / batch helpers: `scheduler: akka.actor.Scheduler` | `scheduler: io.cequence.wsclient.concurrent.AsyncScheduler` |

## The default HTTP engine

In 1.x every client module depended on Play WS on Akka, so every service ran on it. In 2.0.0 the default engine is `jdk`
(`ws-client-jdk`, a dependency of openai-core): `java.net.http`, no actor system, streaming included. An engine you add
wins - the highest priority on the classpath is used - and `-Dws-client.engine=<id>` (or the config key
`ws-client.engine`) names one. See [Choosing an engine](http-engine.md#choosing-an-engine).

- A service that streams needs an engine that streams. The streamed factories pick the highest-priority engine that
  does: next to `play-akka`, `play-pekko` or `sttp`, which do not stream, streams run on `jdk`. `ws-client.engine` applies
  to the streamed services as well, so name a streaming engine there - a non-streaming one fails the streamed factories.
  The OpenAI streamed factories' `withEngine` take any engine and fail with an `OpenAIScalaClientException` naming the
  engines that stream when given one that does not; the Anthropic, Gemini and Perplexity factories' `withEngine` take
  `WSClientEngine with WSClientOutputStreamCore` (in 1.x `... with WSClientOutputStreamExtraAkka`, a subtype, so a 1.x
  engine still fits).
- The `jdk` engine differs from Play WS:
  - It speaks HTTP/2 where the server offers it (Play WS speaks HTTP/1.1); `TransportSettings.httpVersion` pins a version.
  - `requestTimeout` bounds a non-streamed call including its body, a stream fails after `readTimeout` without data, and
    `pooledConnectionIdleTimeout` is ignored - see
    [Timeouts, proxy and HTTP version](http-engine.md#timeouts-proxy-and-http-version).
  - Multipart bodies (file uploads, audio transcriptions) are built in memory.
  - Play's `play.shaded.ahc.org.asynchttpclient.*` system properties do not apply to it.
  - Headers that `java.net.http` sets itself (`Host`, `Connection`, `Content-Length`, `Expect`, `Upgrade`) are dropped
    from your extra headers, with a one-time warning.
- Downloads are binary-safe on every engine (1.x needed a Play engine for them); an engine without output streaming
  serves a download's body as one chunk.
- Sharing one engine across services and providers works as in 1.x - see [Sharing one engine](http-engine.md#sharing-one-engine).
  `WSClientEngineRegistry.outputStreamed()` replaces `StreamedEngineRegistry.outputStreamed()` (below); an engine from
  the latter (ws-client-core-akka / -pekko) is still accepted by every `withEngine`.

## Renames

| 1.7.0 | 2.0.0 |
|---|---|
| `Source[A, NotUsed]`, `Flow[A, B, NotUsed]`, `Source[ByteString, _]` | `AsyncStream[A]`, `AsyncPipe[A, B]`, `AsyncStream[Bytes]` (`io.cequence.wsclient.stream`) |
| `StreamedEngineRegistry.outputStreamed()` (ws-client-core-akka, an Akka-typed engine) | `WSClientEngineRegistry.outputStreamed()` (`io.cequence.wsclient.service.spi`, any engine that streams) |
| `PollingHelper` (ws-client-core-akka, Akka `Scheduler`) | `PollingHelperCore` (`io.cequence.wsclient.service`, `AsyncScheduler`) |
| `SigningWSClientEngine(..., renderJson = PlayJsonUtil.wsClientStringify, ...)` | no `renderJson`: the signer signs the body its engine sends (`engine.renderJsonBody(json)`) |
| `ChatChunk.ChatChunkSourceOps` | `ChatChunk.ChatChunkStreamOps` |
| `ChatChunk.assembleSink: Sink[ChatChunk, Future[AssembledChatCompletion]]` | `AsyncPipe[ChatChunk, AssembledChatCompletion]` (`stream.via(assembleSink).runHead`, or `stream.assembled`) |
| `AwsEventStreamBytesDecoder.flow`, `AwsEventStreamEventParser.flow` | `.pipe` |
| `WSClientWithEngineOutputStreamingBase` (a service base) | `WSClientWithEngineAsyncStreamingBase` |

`PollingHelper`, `StreamedEngineRegistry` and `PlayJsonUtil` still exist in ws-client's Akka / Play modules, which 2.0.0
no longer brings - add them yourself to keep using them.

## Claude agent client

`ClaudeAgentServiceFactory.startSession(settings)` takes only an implicit `ExecutionContext`. `events` stays hot - the one
exception to cold streams next to a download's body: every run joins the live session, with the `system`/`init` message
replayed first, so run it before you send a turn whose events you need. The slowest running consumer paces the others;
with none, the events are read and dropped. On Akka, `events.toSource` crosses one bridge, at the session's hub.

Before (1.7.0):

```scala
implicit val system: ActorSystem = ActorSystem()
implicit val materializer: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val service = ClaudeAgentServiceFactory.startSession(ClaudeAgentSettings(model = Some("claude-haiku-4-5")))
val observed = service.events.runForeach(event => println(event))
```

After (2.0.0):

```scala
import io.cequence.openaiscala.claudeagent.domain.ClaudeAgentSettings
import io.cequence.openaiscala.claudeagent.service.ClaudeAgentServiceFactory

import scala.concurrent.{ExecutionContext, Future}

implicit val ec: ExecutionContext = ExecutionContext.global

val service = ClaudeAgentServiceFactory.startSession(ClaudeAgentSettings(model = Some("claude-haiku-4-5")))

val observed: Future[Unit] = service.events.runForeach(event => println(event)) // join before sending

service.ready.flatMap(_ => service.send("Explain Scala's Option in one sentence."))
```

## The guice module

`AkkaModule` is gone (it bound an `ActorSystem`, a `Materializer` and an execution context from the actor system's
`blocking-dispatcher`). `ExecutionContextModule` binds the `ExecutionContext` the services run their callbacks on: a
fixed pool of daemon threads sized by `openai-scala-client.guice.threads` (default 40). `BaseOpenAIClientApp` keeps
`executionContext` and drops `system` and `materializer`; `GuiceContainer.terminate()` shuts the pool down.

Before (1.7.0):

```scala
override protected def modules: Seq[Module] = Seq(new ConfigModule(), new AkkaModule(), new ServiceModule())
```

After (2.0.0):

```scala
override protected def modules: Seq[Module] = Seq(new ConfigModule(), new ExecutionContextModule(), new ServiceModule())
```

```
# application.conf - replaces the blocking-dispatcher block
openai-scala-client.guice.threads = 40
```

An application that needs an `ActorSystem` for itself binds its own.

## Other changes

- Perplexity's `downloadAgentResponseFile`: an error status fails without the request id (`x-request-id`) the other calls'
  exceptions carry.
- `OpenAIChatCompletionStreamedOutputConversionAdapter`: the message conversion must be 1:1 - a pipe that emits more than
  one element per input fails the stream (1.x zipped the elements out of step).
- Vertex AI's sync calls no longer hold a thread blocked while the SDK answers.

The full list, with the fixes and the new models, is in the [changelog](../CHANGELOG.md).
