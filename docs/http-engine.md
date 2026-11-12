# HTTP engines 🔌

[← back to the README](../README.md)

The HTTP client the library runs on and how to pick another, one engine shared by many services and providers,
timeouts, proxy and HTTP version, and embedding the library in an Akka, Pekko or ZIO application.

Every HTTP call goes through an **engine** of [ws-client](https://github.com/cequence-io/ws-client) 1.2.0, which is
one HTTP backend behind a common contract. 🔥 Since 2.0.0 the default engine is `jdk`, built on the JDK's own
`java.net.http` client. It comes with `openai-scala-core`, needs no Akka and no actor system, and supports streaming.
To use another engine, add its artifact: engines are picked up from the classpath. An engine keeps no state for any one
service, since each call carries its own base URL and credentials. So one engine can serve any number of services and
providers.

Upgrading from 1.x, which ran on Play WS and Akka? See [Migrating to 2.0](migration-2.0.md) 🔥.

## Choosing an engine

| Engine id | Artifact | HTTP client | Scala | Priority | Streams |
|---|---|---|---|---|---|
| `play-pekko-stream` | `ws-client-play-pekko-stream` | Play WS 3 on Pekko | 2.13, 3.3+ | 21 | Yes |
| `play-pekko` | `ws-client-play-pekko` | Play WS 3 on Pekko | 2.13, 3.3+ | 20 | – |
| `pekko-http` | `ws-client-pekko-http` | pekko-http | 2.13, 3.3+ | 15 | Yes |
| `play-akka-stream` | `ws-client-play-akka-stream` | Play WS 2 on Akka | 2.12, 2.13, 3 | 11 | Yes |
| `play-akka` | `ws-client-play-akka` | Play WS 2 on Akka | 2.12, 2.13, 3 | 10 | – |
| `zio-http` | `ws-client-zio-http` | zio-http (Netty) on the ZIO runtime | 2.12, 2.13 | 7 | Yes |
| `sttp` | `ws-client-sttp` | sttp client4, on any sttp `Future` backend | 2.12, 2.13 | 5 | – |
| `jdk` (the default) | `ws-client-jdk`, comes with the library | `java.net.http` (JDK 11+) | 2.12, 2.13, 3 | 0 | Yes |

The full table, with capabilities and notes per backend, is on ws-client's
[Engines](https://github.com/cequence-io/ws-client/blob/main/docs/engines.md) page.

To use an engine, add its artifact next to the library. Every ws-client artifact on a classpath must have the same
version, which is 1.2.0, the version this library is built on:

```scala
libraryDependencies ++= Seq(
  "io.cequence" %% "openai-scala-client-stream" % "2.0.0",
  "io.cequence" %% "ws-client-play-akka-stream" % "1.2.0" // the Akka engine (priority 11) wins over jdk (0)
)
```

**How the engine is picked.** Each plain factory call (`OpenAIServiceFactory()`, `AnthropicServiceFactory()`, ...)
resolves a private engine for its service in this order:

1. the config key `ws-client.engine`, set in `application.conf` (`ws-client.engine = "play-pekko-stream"`) or as a
   system property (`-Dws-client.engine=play-pekko-stream`, which takes precedence);
2. otherwise the engine with the highest priority on the classpath. A tie is an error that names the tied engines, and
   setting the key resolves it.

When you build an engine yourself, you can name it with an explicit id, which takes precedence over both:
`WSClientEngineRegistry(TransportSettings(), Some("play-pekko"))` or
`WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("zio-http"))`. Then pass the engine to a factory's
`withEngine` (see [Sharing one engine](#sharing-one-engine)). `WSClientEngineRegistry.providers` lists the engine
providers found on the classpath (id, priority, capabilities), highest priority first. All of these are in
`io.cequence.wsclient.service.spi`.

**Streams need an engine that streams.** A service that streams resolves its engine among the engines that stream.
These are the services of the `withStreaming` factories (and of `OpenAIStreamedServiceFactory` /
`OpenAIChatCompletionStreamedServiceFactory`), and the Anthropic, Gemini and Perplexity services.

- With `play-akka` and `jdk` on the classpath, `OpenAIServiceFactory()` runs on `play-akka` and
  `OpenAIServiceFactory.withStreaming()` runs on `jdk`. To stream on Akka as well, add the `-stream` artifact.
- The config key applies to every lookup. If it names an engine that does not stream, every factory that streams fails
  with "does not support OutputStreaming".
- `withEngine` on a factory that streams rejects an engine that does not stream. The Anthropic, Gemini and Perplexity
  factories reject it at compile time, because they take a `WSClientEngine with WSClientOutputStreamCore`. The
  `withStreaming` factories reject it when the service is built.
- An engine that does not stream still serves the downloads of the non-streamed services (`retrieveFileContentAsSource`,
  `createAudioSpeech`). It reads the whole body first and then hands it on as a stream of one element.

**Akka or Pekko, never both.** The Akka and Pekko artifacts share package and class names, so that code compiles against
either flavor. As a result, they must never be on one classpath together: which classes load would then depend on the
jar order.

- When ws-client finds both flavors, it logs a warning that names the conflicting jars.
- `ws-client.strict-classpath = true` (or `-Dws-client.strict-classpath=true`) makes engine resolution fail instead.
  This is recommended for CI.
- The artifacts without a flavor go with either: `ws-client-jdk`, `-sttp`, `-zio-http`, `-zio` and `-fs2`.

See ws-client's [Compatibility](https://github.com/cequence-io/ws-client/blob/main/docs/compatibility.md) page.

**Which one?**

- **An Akka application:** `play-akka-stream`. Its streams are Akka `Source`s: converted with `.toSource`, a stream comes
  back as the engine's own `Source`, with no bridge. A `jdk` stream converted the same way crosses one
  `Flow.Publisher` bridge per request. On Scala 2.13 this engine needs Java 11+. This is also the engine 1.x ran on.
- **A Pekko application:** `play-pekko-stream`, or `pekko-http` if you want no Play WS layer. `pekko-http` ignores
  `proxyURL` and logs a warning. On Scala 3 the Pekko engines need 3.3+.
- **No Akka, no Pekko, fewest dependencies:** `jdk`. There is nothing to add (see [The `jdk` engine](#the-jdk-engine)).
- **A ZIO application:** `zio-http` (Scala 2.12 / 2.13). Its streams are native ZIO `ZStream`s, also through the
  [ZIO services](zio.md) 🔥.
- **fs2 / cats-effect:** there is no fs2 engine. Use `jdk` with `toFs2`
  ([Consuming a stream](streaming.md#consuming-a-stream-akka-pekko-fs2-zio)).
- **Any other HTTP stack** (OkHttp, Armeria, ...): `sttp` on that stack's sttp `Future` backend. It serves only the
  non-streamed services. Streams go through `jdk`, or through another engine that streams.

```scala
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.wsclient.service.ws.SttpWSClientEngine
import sttp.client4.Backend

import scala.concurrent.{ExecutionContext, Future}

implicit val ec: ExecutionContext = ExecutionContext.global
implicit val backend: Backend[Future] = ??? // your stack's sttp Future backend

val engine = SttpWSClientEngine() // connect timeout and proxy are set on the backend itself
val service = OpenAIServiceFactory.withEngine(engine) // non-streamed calls only

service.close() // leaves the engine open
engine.close()  // closes the engine AND its backend, including one you passed in
```

## Sharing one engine

Each plain factory call creates a private engine for its service and closes it when the service is closed.

- With `jdk` a private engine is cheap: there is no actor system, but each engine has its own `java.net.http` client and
  connection pool.
- With an Akka or Pekko engine on the classpath, each service gets its own daemon-threaded actor system.

Either way, build services once and share them. Do not create a service per request.

To share ONE engine (one HTTP client and connection pool) across any number of services, including services of
different providers, build the engine once and pass it to the factories' `withEngine`:

```scala
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory,
  OpenAIServiceFactory
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.WSClientEngineRegistry

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

// the highest-priority engine that streams - jdk unless you added another
val engine = WSClientEngineRegistry.outputStreamed()

val openAI = OpenAIServiceFactory.withStreaming.withEngine(engine) // API key from config / env
val anthropic = AnthropicServiceFactory.asOpenAI(AnthropicServiceFactory.withEngine(engine))
val gemini = GeminiServiceFactory.asOpenAI(GeminiServiceFactory.withEngine(engine))
val groq = OpenAIChatCompletionServiceFactory.withStreaming.withEngine(
  engine,
  ChatProviderSettings.groq.coreUrl,
  WsRequestContext(authHeaders = Seq("Authorization" -> s"Bearer ${sys.env("GROQ_API_KEY")}"))
)
val jev = TypeSafeServiceFactory.withEngine(engine) // a decision model, no streaming needed

// ... use the services ...

anthropic.close() // leaves the shared engine open - the other services keep working
openAI.close()
gemini.close()
groq.close()
jev.close()
engine.close() // the one teardown, once every service on it is closed
```

Who closes what:

- A service on an engine you supplied never closes that engine. `service.close()` releases the service only.
- `engine.close()` is the one teardown, called once after all its services are closed.
- An engine found on the classpath (`WSClientEngineRegistry`) owns what it created and releases it on `close()`. For an
  Akka or Pekko engine that is its actor system, and for `zio-http` its Netty driver. The `jdk` engine has nothing to
  release.
- The private engine of a plain factory call is closed by its service.

The factory methods that take an engine:

- **OpenAI and OpenAI-compatible:**
  - `OpenAIServiceFactory`: `withEngine`, `customEngineInstance` (any base URL), `forAzureWithApiKeyAndEngine`,
    `forBedrockWithEngine` and `forAwsSigV4CustomWithEngine`. With SigV4 credentials, the Bedrock variants sign each
    request on top of the engine you pass.
  - `OpenAICoreServiceFactory.withEngine`.
  - `OpenAIChatCompletionServiceFactory`: `withEngine` and `forAzureAIWithEngine`.
  - The same methods on `OpenAIServiceFactory.withStreaming` and `OpenAIChatCompletionServiceFactory.withStreaming`,
    which need an engine that streams.
- **Anthropic:** `withEngine`, `forBedrockMantleWithEngine` (a Bedrock API key), `bedrockAsOpenAIWithEngine` and
  `bedrockAsOpenAIWithBatchSupportAndEngine` (both sign each request with SigV4 on top of the engine you pass).
  `asOpenAI(service)` turns a native service into the OpenAI-shaped one.
- **Gemini:** `GeminiServiceFactory.withEngine`, then `asOpenAI(service)`.
- **Perplexity:** `SonarServiceFactory.withEngine`.
- **Vertex AI:** `VertexAIServiceFactory.batchPredictionWithEngine`.
- **Decision models:** `TypeSafeServiceFactory` with `withEngine`, `withEngine(engine, provider)`, `liquidWithEngine`,
  `perplexityWithEngine` and `microsoftFoundryWithEngine`.

Some calls do not go through an engine, so its settings do not apply to them:

- Vertex AI chat, which uses Google's SDK over gRPC;
- Gemini's file upload and download, which use a plain `HttpURLConnection`;
- the batch files in cloud storage - the S3 objects of Anthropic's Bedrock batches and the Cloud Storage objects of
  Vertex AI batches - and the STS session token the `...WithSessionToken` Bedrock factories mint, all over a plain
  `HttpURLConnection` too;
- the Claude agent client, which runs the `claude` CLI as a subprocess.

## Timeouts, proxy and HTTP version

The client-level settings of an engine are its `TransportSettings` (`io.cequence.wsclient.service.spi`). They are read
once, when the engine or its client is built, and they apply to every service on that engine. They cannot be changed
per call or per service.

- `timeouts: Timeouts` (`io.cequence.wsclient.service.ws.Timeouts`): `requestTimeout`, `readTimeout`, `connectTimeout`
  and `pooledConnectionIdleTimeout`, all `Option[Int]` in **milliseconds**.
- `proxyURL: Option[String]`: `host:port` or `scheme://host:port`. Only the host and the port are used. The proxy is
  always a plain HTTP proxy, and `https` targets are tunnelled through it. Proxy authentication is not supported.
  - `pekko-http` ignores this setting and logs a warning.
  - `zio-http` still resolves the target host through local DNS.
  - No factory takes a proxy: set it on an engine and pass the engine to `withEngine`.
- `httpVersion: Option[HttpVersion]` 🔥: `HttpVersion.Http11` or `HttpVersion.Http2`. Only the `jdk` engine honours it.

For a single service, the plain factories take `timeouts: Option[Timeouts]` (OpenAI, Anthropic, Gemini, decision
models, ...), and no engine code is needed. Perplexity's `SonarServiceFactory` has no such parameter: give it an engine
with the timeouts through `withEngine`.

```scala
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

val service = OpenAIServiceFactory(
  apiKey = "your_api_key",
  timeouts = Some(Timeouts(requestTimeout = Some(120000), readTimeout = Some(120000))) // 120 s
)
```

`OpenAIServiceFactory()` and `OpenAIServiceFactory.withStreaming()` read the timeouts from the config file instead. The
keys there are in seconds: `openai-scala-client.timeouts.requestTimeoutSec` and `readTimeoutSec` (both 200 by default),
`connectTimeoutSec` and `pooledConnectionIdleTimeoutSec` (unset by default, so the engine's defaults apply).

A shared engine gets its settings when you build it. A `withEngine` factory has no timeouts parameter: the engine's
settings apply. If one service needs other settings than the rest, give it an **engine copy**. A copy builds its own
HTTP client but runs on the original's execution environment, so you do not pay for a second actor system:

```scala
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

val engine = WSClientEngineRegistry.outputStreamed() // default timeouts

// e.g. a provider that needs much longer timeouts than the rest, behind a proxy
val slowEngine = engine.copy(
  TransportSettings(
    timeouts = Timeouts(
      requestTimeout = Some(300000), // 300 s
      readTimeout = Some(300000), // 300 s
      connectTimeout = Some(20000) // 20 s
    ),
    proxyURL = Some("proxy.internal:3128")
  )
)

val fastService = OpenAIServiceFactory.withStreaming.withEngine(engine)
val slowService = AnthropicServiceFactory.asOpenAI(AnthropicServiceFactory.withEngine(slowEngine))

slowService.close()
fastService.close()
slowEngine.close() // the copy's own HTTP client
engine.close() // the original last: it owns the execution environment the copy runs on
```

`engine.copy(settings, reuseExecContext = false)` gives the copy its own execution environment too. An engine found on
the classpath can do that; one built on your own actor system throws instead. For `jdk` and `sttp`, which have no
actor system, the flag makes no difference.

How each engine applies the timeouts:

| Setting | Play engines | `jdk` | `zio-http` | `pekko-http` | `sttp` |
|---|---|---|---|---|---|
| `connectTimeout` | yes (default 5 s) | yes (default 5 s) | yes (default 5 s) | yes | yes; on your own backend, set it there |
| `requestTimeout` | the whole exchange, a stream's included (default 60 s) | a plain call's whole exchange, body included; for a stream, the wait for the response headers (default 2 min) | as `jdk` (default 2 min) | the wait for the response (default 2 min) | per request |
| `readTimeout` | no bytes for this long (default 60 s) | a stream fails if no data arrives for this long while its consumer waits (default: the request timeout) | no bytes for this long on the connection, also between two events of a stream (default 2 min) | reading a plain call's body (default 2 min) | ignored |
| `pooledConnectionIdleTimeout` | how long an idle pooled connection is kept (default 60 s) | ignored | as Play (default 60 s) | ignored | ignored |

On the Play engines, which 1.x ran on, `requestTimeout` cuts off any stream that runs longer. On `jdk` and
`zio-http` a long stream runs as long as data keeps arriving. However, a model that thinks for minutes before it streams
anything needs a `readTimeout` longer than that pause.

### The `jdk` engine

- **Always present.** It is a dependency of `openai-scala-core`. With priority 0, any engine you add takes precedence.
  For streamed services, any added engine that streams does.
- **No actor system.** An engine found on the classpath runs its future callbacks on `ExecutionContext.global`.
  `JdkWSClientEngine(settings)` (`io.cequence.wsclient.service.ws`) runs on the implicit `ExecutionContext` in scope.
  `close()` has nothing to release.
- **HTTP version:** when `httpVersion` is unset, `java.net.http` decides: HTTP/2 where the server offers it, else
  HTTP/1.1. If a server or a proxy in between misbehaves on HTTP/2, set `Some(HttpVersion.Http11)`.
- **Timeouts:** see the table above. `requestTimeout` defaults to 2 minutes and `connectTimeout` to 5 seconds;
  `pooledConnectionIdleTimeout` is ignored.
- **Multipart bodies are built in memory.** This covers file uploads, audio transcriptions and image edits: the whole
  file and form are in memory before they are sent. The Play engines stream these bodies instead.
- **Restricted headers:** `java.net.http` sets `Host`, `Connection`, `Content-Length`, `Expect` and `Upgrade` itself.
  If a request context or a call sets one of them, the header is dropped, with one warning per header name (unless the
  JVM allows it through `jdk.httpclient.allowRestrictedHeaders`).

```scala
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.spi.{HttpVersion, TransportSettings}
import io.cequence.wsclient.service.ws.{JdkWSClientEngine, Timeouts}

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

val engine = JdkWSClientEngine(
  TransportSettings(
    timeouts = Timeouts(readTimeout = Some(600000)), // a stream may stay silent for up to 10 minutes
    httpVersion = Some(HttpVersion.Http11)
  )
)
val service = OpenAIServiceFactory.withStreaming.withEngine(engine)
```

## Embedding in an Akka, Pekko or ZIO application

An engine found on the classpath brings its own execution environment: an Akka or Pekko engine has its own daemon actor
system, and `zio-http` has its own Netty driver. In an application that already runs one, build the engine on the
application's own runtime with the engine's constructor and pass it to `withEngine`. No factory takes a `Materializer`
or an `ActorSystem` 🔥; only the engine does. Neither the engine nor a service on it ever shuts down your runtime.

**Akka** (`ws-client-play-akka-stream`):

```scala
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.wsclient.concurrent.ActorSchedulers._
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.stream.SourceInterop._

import scala.concurrent.ExecutionContext

implicit val system: ActorSystem = ActorSystem("my-app") // your application's actor system
implicit val materializer: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val engine = PlayWSStreamClientEngine() // on YOUR materializer and dispatcher
val service = OpenAIServiceFactory.withStreaming.withEngine(engine)

service
  .createChatCompletionStreamedTyped(
    Seq(UserMessage("Name three Norwegian fjords.")),
    CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
  )
  .texts
  .toSource // the engine's own Source, with our operators as Akka stages - no bridge
  .runWith(Sink.foreach(print))

// retried (non-streamed) calls, waiting between the attempts on your actor system's scheduler
implicit val scheduler: Scheduler = system.scheduler
implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 3)
val withRetries = OpenAIServiceAdapters.forFullService.retry(service)

// when done: withRetries.close() (it closes service too), then engine.close() - that closes the
// Play WS client only, your ActorSystem keeps running
```

Retries, failover and their timeouts schedule timers through an implicit `AsyncScheduler`
(`io.cequence.wsclient.concurrent`). By default this is ws-client's `AsyncScheduler.default`, a single daemon thread,
and you pass nothing. The `ActorSchedulers._` import above turns the implicit actor-system `Scheduler` in scope into
the `AsyncScheduler` the retry adapter takes, so its timers run on your actor system instead.
`AsyncScheduler.fromExecutor(executor)` wraps a `ScheduledExecutorService` of your own. The Pekko artifacts have the
same `ActorSchedulers` import under the same name.

**Pekko** (`ws-client-play-pekko-stream`, Scala 2.13 or 3.3+):

```scala
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

import scala.concurrent.ExecutionContext

implicit val system: ActorSystem = ActorSystem("my-app") // your application's actor system
implicit val materializer: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val engine = PlayWSStreamClientEngine() // Play WS 3 on YOUR system
val anthropic = AnthropicServiceFactory.asOpenAI(AnthropicServiceFactory.withEngine(engine))

anthropic.close()
engine.close() // the Play WS client only - your ActorSystem keeps running
```

To use pekko-http without the Play WS layer (`ws-client-pekko-http`), build the engine with `PekkoHttpWSClientEngine()`.
It runs on your `ActorSystem` and its `Http()` connection pools. Its `close()` leaves both alone.

```scala
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.ws.PekkoHttpWSClientEngine
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.ExecutionContext

implicit val system: ActorSystem = ActorSystem("my-app")
implicit val ec: ExecutionContext = system.dispatcher

val engine = PekkoHttpWSClientEngine()
val service = OpenAIServiceFactory.withStreaming.withEngine(engine)
```

**ZIO** 🔥 (`ws-client-zio-http`, Scala 2.12 / 2.13). `ZioHttpWSClientEngine.layer()` builds the engine on your
application's zio-http `Client` and runs it on your runtime. It never closes the client. The client's own configuration
applies (connect and idle timeouts, proxy, TLS); of the engine's `TransportSettings` only `requestTimeout` does.
Configure the client's TLS yourself, because zio-http's default `ClientSSLConfig` accepts any certificate. With the
[ZIO services](zio.md) of `openai-scala-zio`:

```scala
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIStreamedServiceZIO}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.ws.ZioHttpWSClientEngine
import zio._
import zio.http.{Client, ClientSSLConfig, ZClient}

import scala.concurrent.ExecutionContext

implicit val ec: ExecutionContext = ExecutionContext.global

// your application's client, verifying certificates against the JVM's trust store
val client: ZLayer[Any, Throwable, Client] =
  ZLayer.succeed(
    ZClient.Config.default
      .ssl(ClientSSLConfig.FromSystemTrustStore)
      // zio-http's default is a fixed pool that opens 10 connections per host up front
      .dynamicConnectionPool(minimum = 1, maximum = 64, ttl = 60.seconds)
  ) >>> Client.fromConfig

// the engine on that client and on your runtime
val engine: ZLayer[Client, Nothing, ZioHttpWSClientEngine] = ZioHttpWSClientEngine.layer()

// the service on the engine, closed with the layer's scope - the engine and the client stay
val openAI: ZLayer[ZioHttpWSClientEngine, Throwable, OpenAIStreamedServiceZIO] =
  ZLayer.service[ZioHttpWSClientEngine].flatMap { env =>
    OpenAIStreamedServiceZIO.layer(() =>
      OpenAIServiceFactory.withStreaming.withEngine(env.get[ZioHttpWSClientEngine])
    )
  }

val app: ZLayer[Any, Throwable, OpenAIStreamedServiceZIO] = client >>> engine >>> openAI
```

On `zio-http` the services' streams are the engine's own `ZStream`s, so the ZIO services hand them on with no bridge.
To build the engine outside a layer, use `ZioHttpWSClientEngine(client)` with an implicit `Runtime[Any]` and
`ExecutionContext`.

How to consume the streams in your own streaming library is covered on the streaming page, under
[Consuming a stream](streaming.md#consuming-a-stream-akka-pekko-fs2-zio). On the matching engine an Akka or Pekko
`Source`, or a ZIO `ZStream`, comes back with no bridge. For ZIO, see [ZIO services](zio.md). For the 1.x setup and the
arguments that changed, see [Migrating to 2.0](migration-2.0.md).
