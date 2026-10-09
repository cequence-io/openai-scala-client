# Sharing an HTTP engine 🔌

[← back to the README](../README.md)

One HTTP client pool and actor system for any number of services and providers; timeouts and proxies, which are
engine-level.

Every plain factory call (`OpenAIServiceFactory()`, `AnthropicServiceFactory()`, ...) spins up
its own HTTP client pool **and** its own dedicated actor system (created eagerly, all daemon
threads, so a leaked service can't block JVM exit) - fine for a handful of long-lived services,
wasteful if you're building many services, or many providers, in the same app. Since the
`ws-client` 1.0 engine-discovery migration you can build **one engine** and share it across
**any number of services, including across different providers**:

```scala
import io.cequence.wsclient.service.spi.StreamedEngineRegistry

implicit val ec: ExecutionContext = ExecutionContext.global

val engine = StreamedEngineRegistry.outputStreamed() // one pool + one (daemon) actor system

val openAI = OpenAIServiceFactory.withEngine(engine)       // api key from config/env
val anthropic = AnthropicServiceFactory.withEngine(engine) // api key from env
val gemini = GeminiServiceFactory.withEngine(engine)       // api key from env

// ... use the services ...

anthropic.close() // closes a service on a SHARED engine without touching the engine itself -
                   // openAI and gemini keep working
engine.close()     // the one real teardown - close it once, after every service using it is done
```

**Timeouts (and proxy) are engine-level**, baked into the HTTP client at construction time and
deliberately not overridable per call. All four `Timeouts` fields are in **milliseconds** (the
`*Sec`-suffixed keys in the config file, e.g. `requestTimeoutSec`, are the seconds-based
equivalent - see the Config section above). A service that needs different timeouts than the rest
of a shared setup gets its own **engine copy**, which shares the parent's actor system (so you
don't pay for a second one) but builds its own HTTP client:

```scala
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts

val engine = StreamedEngineRegistry.outputStreamed() // default timeouts

// e.g. a batch/VLM provider that legitimately needs much longer timeouts than the rest
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

slowService.close() // only slowEngine's own HTTP client - the shared actor system lives on
fastService.close()
engine.close()       // tear down the shared actor system last
```

You can also set timeouts on a single, non-shared service directly, without touching engines:

```scala
val service = OpenAIServiceFactory(
  apiKey = "your_api_key",
  timeouts = Some(Timeouts(requestTimeout = Some(120000), readTimeout = Some(120000))) // 120s
)
```

To embed a service into an existing Akka application (one Akka app, one `ActorSystem`), build
the engine on YOUR `Materializer` instead of letting it create its own, so closing the service
never touches your actor system:

```scala
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine

implicit val system: ActorSystem = /* your app's existing ActorSystem */ ???
implicit val materializer: Materializer = Materializer(system)
implicit val ec: ExecutionContext = system.dispatcher

val engine = new PlayWSStreamClientEngine() // runs on YOUR materializer/ec
val service = OpenAIServiceFactory.withEngine(engine)

service.close() // closes only the HTTP client - your ActorSystem is untouched
```

> **Akka backend, for now.** This library's streaming API currently returns
> `Source[T, akka.NotUsed]` and depends on the Akka-flavored `ws-client` engines. `ws-client`
> itself is no longer Akka-only - it also ships Pekko engines, backend-only engines with no
> actor system (JDK, sttp), and a family-neutral streaming core underneath all of them. A live
> experiment already swapped the Akka dependency for the Pekko one and ran **synchronous** calls
> with zero source changes; **streaming** is still Akka-specific in this repo (a few akka types
> baked into the public API) and will likely be abstracted away in a future release so you can
> pick your own backend. Nothing you need to do today - just don't be surprised if the streaming
> API becomes backend-agnostic later.
