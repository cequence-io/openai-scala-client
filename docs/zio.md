# ZIO services ⚡

[← back to the README](../README.md)

🔥 New in 2.0.0: the `openai-scala-zio` module gives every service of the library as a ZIO view - the same methods,
returning `Task` where the service returns a `Future` and `ZStream` where it returns an `AsyncStream`. The views are
generated from the service traits at build time ([ZioServiceGenerator](../project/ZioServiceGenerator.scala)), so
they always have the methods, parameters, defaults and Scaladoc of the services they wrap.

## Installation

```
"io.cequence" %% "openai-scala-zio" % "2.0.0"
```

- Scala 2.12, 2.13 and 3.3+. Its Scala 3 artifact is built with Scala 3.3, as ZIO's and ws-client-zio's are, so a
  Scala 3 project needs 3.3 or later for this module (the library's other modules work on 3.2.2 too).
- It brings `openai-scala-client`, `openai-scala-client-stream` and ws-client-zio (ZIO 2 streams).
- The provider modules are `provided` dependencies: the module pulls in none of them. Add the module of every
  provider whose view you use, e.g. `"io.cequence" %% "openai-scala-anthropic-client" % "2.0.0"`, or
  `openai-scala-all` for all of them.
- The library brings no logging backend; add an SLF4J binding of your choice (see the README).

## The views

A view of the service trait `T` is `TZIO`, in the package of `T`. Its methods map the result types:

| The service returns | The view returns |
|---|---|
| `Future[A]` | `Task[A]` |
| `AsyncStream[A]` | `ZStream[Any, Throwable, A]` |
| `AsyncStream[Bytes]` | `ZStream[Any, Throwable, Byte]` |
| `Future[AsyncStream[Bytes]]` / `Future[Option[AsyncStream[Bytes]]]` (downloads) | `Task[ZStream[Any, Throwable, Byte]]` / `Task[Option[ZStream[Any, Throwable, Byte]]]` |
| anything else (`defaultModel`, `sessionId`, ...) | the same value |

- A callback `A => Future[B]` becomes `A => Task[B]` (tool-approval decisions, `filterModels` of the failover
  helpers); it runs on the view's runtime (`zioRuntime`).
- A method's implicit `ExecutionContext` parameter is dropped: the view passes the executor of the fiber that runs
  it. Other implicit parameters stay (the helpers' `AsyncScheduler`, which needs nothing - see
  [Helpers and callbacks](#helpers-and-callbacks)). The factories still take an implicit `ExecutionContext` for the
  service itself.
- `close` is a `UIO[Unit]` that closes the service behind the view.

The views of the OpenAI services (package `io.cequence.openaiscala.service`, part of the module):

| View | Wraps |
|---|---|
| `OpenAIServiceZIO` | `OpenAIService` - the whole OpenAI API (`OpenAIServiceFactory()`) |
| `OpenAIStreamedServiceZIO` | `OpenAIService with OpenAIStreamedServiceExtra` - the same with streams (`OpenAIServiceFactory.withStreaming()`) |
| `OpenAICoreServiceZIO` | `OpenAICoreService` - models, completions, chat completions, embeddings |
| `OpenAIChatCompletionServiceZIO` | `OpenAIChatCompletionService` - chat completions, also of every provider's `asOpenAI()` adapter |
| `OpenAIChatCompletionStreamedServiceExtraZIO` | `OpenAIChatCompletionStreamedServiceExtra` - the chat streams (OpenAI-shaped and typed), also of the adapters |
| `OpenAIStreamedServiceExtraZIO` | `OpenAIStreamedServiceExtra` - the chat streams plus the completion, Responses API and Agents API streams |
| `OpenAICompletionServiceZIO`, `OpenAIResponsesServiceZIO`, `OpenAIAgentsServiceZIO`, `OpenAIDecisionsServiceZIO`, `OpenAIGraderServiceZIO`, `OpenAIChatCompletionBatchServiceZIO` | the parts `OpenAIService` is made of; `OpenAIDecisionsServiceZIO` also wraps `TypeSafeServiceFactory.asOpenAIDecisions(...)` |

`import io.cequence.openaiscala.service.OpenAIChatCompletionExtraZIO._` adds the JSON and failover helpers
(`createChatCompletionWithJSON`, `createChatCompletionWithJSONFullResponse`, `createChatCompletionWithFailover`,
`createChatCompletionWithFailoverSettings`) to every chat view, as `Task`s - see
[Helpers and callbacks](#helpers-and-callbacks).

The views of the provider services:

| View | Wraps | Add the module |
|---|---|---|
| `AnthropicServiceZIO` | `AnthropicService` - Messages, files, skills, message batches, Managed Agents, environments, sessions, deployments, vaults, memory stores | openai-scala-anthropic-client |
| `AnthropicBedrockBatchInferenceServiceZIO` | `AnthropicBedrockBatchInferenceService` (`AnthropicServiceFactory.bedrockBatchInference(...)`) | openai-scala-anthropic-client |
| `GeminiServiceZIO` | `GeminiService` | openai-scala-google-gemini-client |
| `VertexAIBatchPredictionServiceZIO` | `VertexAIBatchPredictionService` (`VertexAIServiceFactory.batchPrediction(...)`) | openai-scala-google-vertexai-client |
| `SonarServiceZIO` | `SonarService` - Perplexity's Agent API and Sonar | openai-scala-perplexity-sonar-client |
| `TypeSafeServiceZIO` | `TypeSafeService` - the System One decision hosts | openai-scala-typesafe-client |
| `ClaudeAgentServiceZIO` | `ClaudeAgentService` - a session of the `claude` CLI | openai-scala-claude-agent-client |

Each is in its service's package (`io.cequence.openaiscala.anthropic.service`, `...gemini.service`,
`...vertexai.service`, `...perplexity.service`, `...typesafe.service`, `...claudeagent.service`). `AnthropicServiceZIO`
is made of `AnthropicMessageBatchServiceZIO`, `AnthropicManagedAgentServiceZIO`, `AnthropicEnvironmentServiceZIO`,
`AnthropicSessionServiceZIO`, `AnthropicDeploymentServiceZIO`, `AnthropicVaultServiceZIO` and
`AnthropicMemoryStoreServiceZIO`. Vertex AI chat and every provider's OpenAI adapter (`asOpenAI()`) are OpenAI chat
services: take `OpenAIChatCompletionServiceZIO` for their calls and `OpenAIChatCompletionStreamedServiceExtraZIO` for
their streams. Build the adapter once and take both views of it with `apply`; both views close the same adapter, so
close it once.

## Creating a view

Every view has two constructors in its companion.

`apply(service)` wraps a service you built, with an implicit `Runtime[Any]` (`Runtime.default` when none is in
scope). The view does not own the service - close it with `view.close` or `service.close()` when done:

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIServiceZIO}
import zio._

import scala.concurrent.ExecutionContext

object ZioChat extends ZIOAppDefault {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val service = OpenAIServiceZIO(OpenAIServiceFactory()) // the API key from config / env

  override def run =
    service
      .createChatCompletion(
        Seq(UserMessage("What is the capital of Norway?")),
        CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      )
      .flatMap(response => Console.printLine(response.contentHead))
      .ensuring(service.close)
}
```

`layer(() => service)` is a `ZLayer` that builds the service when the layer is acquired, runs the view on the
application's runtime and closes the service with the layer's scope (the views of services that close - see
[Closing](#the-service-behind-a-view-asfuture)). It takes a function, not a by-name parameter (ZIO's `provide` macro
cannot take a by-name call):

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIStreamedServiceZIO}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import zio._

import scala.concurrent.ExecutionContext

object ZioStreamedChat extends ZIOAppDefault {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val openAI: ZLayer[Any, Throwable, OpenAIStreamedServiceZIO] =
    OpenAIStreamedServiceZIO.layer(() => OpenAIServiceFactory.withStreaming())

  private val program: ZIO[OpenAIStreamedServiceZIO, Throwable, Unit] =
    ZIO.serviceWithZIO[OpenAIStreamedServiceZIO] { service =>
      service
        .createChatCompletionStreamedTyped(
          Seq(UserMessage("Name three rivers in Europe.")),
          CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
        )
        .collect { case ChatChunk.Text(text) => text }
        .runForeach(text => Console.print(text))
    }

  override def run = program.provide(openAI)
}
```

A provider's view is built the same way, e.g. Anthropic's native message stream:

```scala
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicServiceFactory, AnthropicServiceZIO}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import zio._

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

val anthropic: ZLayer[Any, Throwable, AnthropicServiceZIO] =
  AnthropicServiceZIO.layer(() => AnthropicServiceFactory()) // ANTHROPIC_API_KEY

val answer: ZIO[AnthropicServiceZIO, Throwable, String] =
  ZIO.serviceWithZIO[AnthropicServiceZIO] { service =>
    service
      .createMessageStreamed(
        Seq(UserMessage("What is the capital of Norway?")),
        AnthropicCreateMessageSettings(NonOpenAIModelId.claude_haiku_5_5, max_tokens = 1000)
      )
      .map(_.text)
      .runFold("")(_ + _)
  }
```

## Laziness: Tasks and streams

- **A `Task` is a description.** Nothing is sent when a view method is called; each run of the `Task` calls the
  service again, so a request method sends a new (possibly billed) request per run - a ZIO `retry` or `repeat`
  re-sends it. A method that answers with state the service already holds (the Claude agent's `ready` and
  `completion`) gives every run the same outcome.
- **A `ZStream` is cold.** Nothing is sent until it runs, and each run is a new request. Interrupting the fiber that
  runs it, or a `take` that stops early, cancels the run, which aborts the HTTP request.
- **Interrupting a `Task` does not abort its request.** A call is `ZIO.fromFuture` over the Future-based service, on
  every engine: interruption stops the wait for the answer, while a request already sent runs to its end.
- **Downloads** (`createAudioSpeech`, `retrieveFileContentAsSource`, `getAgentSessionArtifactContent`, Anthropic's
  `downloadFile`, Perplexity's `downloadAgentResponseFile`): each run of the `Task` starts the download and completes
  once the body began (with `None` for a missing file where the result is an `Option`); the byte `ZStream` it returns
  reads that body: run it once - an abandoned one holds its connection.
- **The Claude agent's `events` are hot.** The CLI session runs from the moment the service is built (with `layer`:
  from the layer's acquisition until its scope closes). Each run of `events` joins the session as a new consumer: it
  starts with the replayed `system`/`init` event, then gets the live events from that moment on. The slowest running
  consumer paces the others; with none, the events are read and dropped. Start the run before sending a turn whose
  events you need:

```scala
import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentEvent, ClaudeAgentSettings}
import io.cequence.openaiscala.claudeagent.service.{ClaudeAgentServiceFactory, ClaudeAgentServiceZIO}
import zio._

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

val agentLayer: ZLayer[Any, Throwable, ClaudeAgentServiceZIO] =
  ClaudeAgentServiceZIO.layer(() =>
    ClaudeAgentServiceFactory.startSession(ClaudeAgentSettings(model = Some("claude-haiku-4-5")))
  )

def turnEnded(event: ClaudeAgentEvent): Boolean =
  event match {
    case _: ClaudeAgentEvent.ResultSuccess => true
    case _: ClaudeAgentEvent.ResultError   => true
    case _                                 => false
  }

val turn: ZIO[ClaudeAgentServiceZIO, Throwable, Chunk[ClaudeAgentEvent]] =
  ZIO.serviceWithZIO[ClaudeAgentServiceZIO] { agent =>
    for {
      subscribed <- Promise.make[Nothing, Unit]
      running <- agent.events
        .tap(_ => subscribed.succeed(()))
        .takeUntil(turnEnded)
        .runCollect
        .fork
      _ <- subscribed.await // the run is live: its first element, the replayed init, arrived
      _ <- agent.send("Explain the difference between Scala's Option and Try in one sentence.")
      events <- running.join
    } yield events
  }
```

## Errors

A `Task` or `ZStream` fails with the exception the Future-based service fails with - classified, never a raw HTTP
status:

- the OpenAI services and every provider's OpenAI adapter with the `OpenAIScala*` hierarchy
  (`OpenAIScalaRateLimitException`, `OpenAIScalaTokenCountExceededException`, ...);
- the native provider services with their own: `AnthropicScala*`, `GeminiScala*`, `PerplexityScala*`,
  `TypeSafeScala*` - e.g. a 429 on Anthropic's message stream fails the `ZStream` with
  `AnthropicScalaRateLimitException`.

A request check the Future API throws synchronously (`systemOne`'s `IllegalArgumentException`) fails the `Task` too;
it is not a defect. ZIO's own retries work on these failures - each retry is a new request:

```scala
import io.cequence.openaiscala.{OpenAIScalaClientException, Retryable}
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceZIO
import zio._

def retryable(error: Throwable): Boolean =
  error match {
    case e: OpenAIScalaClientException => Retryable(e) // rate limits, timeouts, server errors
    case _                             => false
  }

def answer(service: OpenAIServiceZIO): Task[ChatCompletionResponse] =
  service
    .createChatCompletion(
      Seq(UserMessage("What is the capital of Norway?")),
      CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
    )
    .retry(
      Schedule.recurs(3) && Schedule.exponential(1.second) && Schedule.recurWhile[Throwable](retryable)
    )
```

## Helpers and callbacks

The JSON and failover helpers come with one import and return `Task`s:

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionExtraZIO._
import io.cequence.openaiscala.service.OpenAIStreamedServiceZIO
import play.api.libs.json.{Format, Json}
import zio._

final case class City(city: String)

object City {
  implicit val format: Format[City] = Json.format[City]
}

val capital: ZIO[OpenAIStreamedServiceZIO, Throwable, City] =
  ZIO.serviceWithZIO[OpenAIStreamedServiceZIO] { service =>
    service.createChatCompletionWithJSON[City](
      Seq(UserMessage("Which city is the capital of Norway?")),
      CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
    )
  }
```

Human approval of tool calls mid-stream takes a ZIO decision: it runs on the view's runtime when the run pauses, its
answer resumes the run, and the rounds come back as one `ZStream` (a failed decision fails the stream). See
[Streaming](streaming.md) for the approval flow itself.

```scala
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtraZIO
import zio._
import zio.stream.ZStream

def approved(
  service: OpenAIChatCompletionStreamedServiceExtraZIO,
  messages: Seq[BaseMessage],
  tools: Seq[ChatCompletionTool],
  settings: CreateChatCompletionSettings
): ZStream[Any, Throwable, ChatChunk] =
  service.createChatToolCompletionStreamedWithApprovals(messages, tools, settings = settings) { request =>
    Console.printLine(s"Approving ${request.toolName}").as(request.approve)
  }
```

The helpers' timers (failover deadlines, retry delays) run on the implicit `AsyncScheduler`
(`io.cequence.wsclient.concurrent`), whose default needs nothing. To run them on ZIO's clock instead, put
`implicit val scheduler: AsyncScheduler = ZioInterop.scheduler(runtime)` (`io.cequence.wsclient.stream.ZioInterop`,
with your `Runtime[Any]`) in scope.

## The service behind a view: `asFuture`

`view.asFuture` is the Future-based service the view wraps - for code that takes the Future API, or for a member the
view leaves as it is:

- **Adapters** (retry, round robin, routers, guardrails - see [Adapters](adapters.md)) wrap the Future-based service:
  build the stack first, then take the view of the result.
- **Closing:** `view.close` closes `asFuture`. The traits that are parts of a larger service - the Responses, Agents,
  Grader and chat-completion batch services, and Anthropic's sub-services - have no lifecycle of their own, so their
  views' `close` closes nothing and they have no `layer`. Take the view of the whole service instead
  (`OpenAIServiceZIO`, `AnthropicServiceZIO`): it has all their methods.
- **Helpers without a ZIO form:** the chat batch helpers (`createChatCompletionBatchWithJSON`,
  `createChatCompletionBatchAndWaitForResults`) and the decision routines `decide` / `rerank`
  (`io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._`) work on the Future-based service - call them on
  `asFuture` inside `ZIO.fromFuture`, e.g. `ZIO.fromFuture(implicit ec => decisions.asFuture.rerank(query, passages))`.
- **Your own streams:** to turn any `AsyncStream` into a `ZStream`, `import io.cequence.wsclient.stream.ZioInterop._`
  and call `.toZStream` (`.toZByteStream` for bytes) - see
  [Consuming a stream: Akka, Pekko, fs2, ZIO](streaming.md#consuming-a-stream-akka-pekko-fs2-zio).

## Native ZIO: the zio-http engine

The views work on any engine. Which engine runs them decides whether a stream crosses a bridge:

- **On the default `jdk` engine** an HTTP stream is a `java.util.concurrent.Flow.Publisher`: the view hands it to
  ZIO through one bridge per request, and the operators after it run as ZIO stages.
- **On the `zio-http` engine** (ws-client-zio-http, zio-http on Netty and the ZIO runtime; Scala 2.12 and 2.13) an
  HTTP stream is the engine's own `ZStream`: the view hands it on with the operators as ZIO stages - no bridge, for
  typed chat chunks, raw byte streams and downloads alike (a download's up-front status check runs inside ZIO and
  keeps the native stream). [ZioHttpNativeRoundTripSpec](../openai-zio/src/test/scala-2/io/cequence/openaiscala/service/ZioHttpNativeRoundTripSpec.scala)
  counts the bridges with `StreamDiagnostics.counted` (`io.cequence.wsclient.stream`).
- A plain call is `ZIO.fromFuture` on every engine: on `zio-http` the engine runs it as a ZIO effect to a `Future`,
  and interrupting the `Task` does not abort the request.
- The Claude agent's `events` are no HTTP stream: they are fanned out by ws-client's own interpreter, so a `ZStream` of
  them crosses one bridge on every engine.

To run on `zio-http`, add the engine with the same version as the other ws-client artifacts:

```
"io.cequence" %% "ws-client-zio-http" % "1.2.0"
```

Every factory then picks it (priority 7, above `jdk`'s 0 - an Akka or Pekko engine on the classpath ranks higher;
Vertex AI chat runs on Google's SDK, outside ws-client), so
`OpenAIStreamedServiceZIO.layer(() => OpenAIServiceFactory.withStreaming())` runs natively as it is. The engine can
also be named: `-Dws-client.engine=zio-http`, or
`WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("zio-http"))` passed to a factory's `withEngine` - see
[Choosing an engine](http-engine.md#choosing-an-engine). Such an engine owns its zio-http client and verifies server
certificates against the JVM's trust store.

A ZIO application can run the engine on its own zio-http `Client` instead: `ZioHttpWSClientEngine.layer()` is a
`ZLayer[Client, Nothing, ZioHttpWSClientEngine]` on the application's runtime and executor (or
`ZioHttpWSClientEngine(client)` with an implicit `Runtime[Any]` and `ExecutionContext`). The engine never closes that
client, and a service on it does not close the engine. The client's own configuration applies - connect and idle
timeouts, proxy, TLS, connection pool (zio-http's default is a fixed pool of 10 connections); of the engine's
`TransportSettings` only the per-call `requestTimeout` does. Configure the client's TLS yourself: zio-http's default
trusts any certificate.

```scala
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIStreamedServiceZIO}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.ws.ZioHttpWSClientEngine
import zio._
import zio.http.{Client, ClientSSLConfig, ZClient}

import scala.concurrent.ExecutionContext

object ZioNativeChat extends ZIOAppDefault {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  // the application's client, verifying certificates against the JVM's trust store
  private val client: ZLayer[Any, Throwable, Client] =
    ZLayer.succeed(
      ZClient.Config.default
        .ssl(ClientSSLConfig.FromSystemTrustStore)
        // zio-http's default is a fixed pool that opens 10 connections per host up front
        .dynamicConnectionPool(minimum = 1, maximum = 64, ttl = 60.seconds)
    ) >>> Client.fromConfig

  // the OpenAI service on an engine over that client, closed with the layer's scope
  private val openAI: ZLayer[ZioHttpWSClientEngine, Throwable, OpenAIStreamedServiceZIO] =
    ZLayer.scoped[ZioHttpWSClientEngine](
      for {
        engine <- ZIO.service[ZioHttpWSClientEngine]
        runtime <- ZIO.runtime[Any]
        service <- ZIO.acquireRelease(
          ZIO.attempt(
            OpenAIStreamedServiceZIO(OpenAIServiceFactory.withStreaming.withEngine(engine))(runtime)
          )
        )(_.close)
      } yield service
    )

  private val program: ZIO[OpenAIStreamedServiceZIO, Throwable, Unit] =
    ZIO.serviceWithZIO[OpenAIStreamedServiceZIO] { service =>
      service
        .createChatCompletionStreamedTyped(
          Seq(UserMessage("Name three rivers in Europe.")),
          CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
        )
        .collect { case ChatChunk.Text(text) => text }
        .runForeach(text => Console.print(text))
    }

  override def run = program.provide(client, ZioHttpWSClientEngine.layer(), openAI)
}
```

On Scala 3 there is no `zio-http` engine (ws-client-zio-http is published for Scala 2.12 and 2.13): the views run on
`jdk` (or an Akka / Pekko engine), one bridge per HTTP stream. Embedding the library's engines in an existing ZIO,
Akka or Pekko application is covered in [HTTP engines](http-engine.md#embedding-in-an-akka-pekko-or-zio-application).

Examples to start from are the module's tests:
[OpenAIServiceZIOWireSpec](../openai-zio/src/test/scala/io/cequence/openaiscala/service/OpenAIServiceZIOWireSpec.scala)
(lazy Tasks, cold streams, downloads, the JSON helper, the layer),
[ProviderServicesZIOWireSpec](../openai-zio/src/test/scala/io/cequence/openaiscala/service/ProviderServicesZIOWireSpec.scala)
(Anthropic, a decision service, the Claude agent's hot events) and
[ToolApprovalZIOSpec](../openai-zio/src/test/scala/io/cequence/openaiscala/service/ToolApprovalZIOSpec.scala).
