# FAQ 🤔

[← back to the README](../README.md)


1. _Wen Scala 3?_ 

   ~~Feb 2023. You are right; we chose the shortest month to do so :)~~
 **Done!**


2. _I got a timeout exception. How can I change the timeout setting?_

   You can do it either by passing the `timeouts` param to `OpenAIServiceFactory` (a `Timeouts` of
   `io.cequence.wsclient.service.ws`, all its fields in milliseconds) or, if you use your own configuration file, then you can simply add it there (in seconds) as: 

```
openai-scala-client {
    timeouts {
        requestTimeoutSec = 200
        readTimeoutSec = 200
        connectTimeoutSec = 5
        pooledConnectionIdleTimeoutSec = 60
    }
}
```

   A service on a shared engine has the engine's timeouts - see
   [HTTP engines](http-engine.md#timeouts-proxy-and-http-version).

3. _I got an `OpenAIScalaClientException: API key is not defined in the config at 'openai-scala-client.apiKey'`. What should I do?_

   Set the env. variable `OPENAI_SCALA_CLIENT_API_KEY`, or pass the key to the factory (`OpenAIServiceFactory("your-api-key")`). If you don't have one register [here](https://beta.openai.com/signup).


4. _A stream failed with "A streamed frame (one event or line of the stream) exceeds the limit of 32 MiB". What should I do?_

   One event of the stream - e.g. a generated image in base64 - was larger than the cap. Raise it in your configuration
   file or with the env. variable `OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH` (e.g. `128 MiB`):

```
openai-scala-client {
    streaming {
        maxFrameLength = 128 MiB
    }
}
```


5. 🔥 _Which HTTP engine should I use?_

   The default - the JDK's `java.net.http` client (engine `jdk`), which comes with the library and needs no actor
   system - serves most applications, streaming included. Another engine takes over once its ws-client module is on
   the classpath (the highest priority wins), or when named with the config key / system property `ws-client.engine`
   (e.g. `-Dws-client.engine=jdk`). Streamed services need an engine that streams - `jdk`, `zio-http`,
   `play-akka-stream`, `play-pekko-stream` or `pekko-http` - and take the highest-priority one of those; `sttp`,
   `play-akka` and `play-pekko` serve only the non-streamed calls (with only one of them added, the streams stay on
   `jdk`). In an Akka or Pekko application `ws-client-play-akka-stream` / `ws-client-play-pekko-stream` (or
   `ws-client-pekko-http`) run the streams natively on Akka / Pekko - `.toSource` then hands over the engine's own
   `Source`, without a bridge; in a ZIO application `ws-client-zio-http` does the same for `.toZStream`. Not every
   engine is built for every Scala version (`zio-http` for 2.12 / 2.13 only, the Pekko engines for 2.13 / 3.3+). Keep
   every ws-client artifact on one version, and never put the Akka and Pekko flavors on one classpath. See
   [HTTP engines](http-engine.md#choosing-an-engine) and, for the interop imports,
   [Streaming](streaming.md#consuming-a-stream-akka-pekko-fs2-zio).


6. _It all looks cool. I want to chat with you about your research and development?_

   Just shoot us an email at [openai-scala-client@cequence.io](mailto:openai-scala-client@cequence.io?subject=Research%20andDevelopment).
