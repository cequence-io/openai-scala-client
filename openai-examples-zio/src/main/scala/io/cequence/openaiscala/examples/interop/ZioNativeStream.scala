package io.cequence.openaiscala.examples.interop

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIStreamedServiceZIO}
import io.cequence.wsclient.service.ws.ZioHttpWSClientEngine
import zio.http.{Client, ClientSSLConfig, ZClient}
import zio.{durationInt, Console, ZIO, ZIOAppDefault, ZLayer}

/**
 * ZIO in, ZIO out: the zio-http engine runs on the application's own `Client` and runtime, and
 * the ZIO view of the service hands its streams back as that engine's own `ZStream`s - the
 * library's operators run as ZIO stages, with no bridge through `Flow.Publisher`.
 *
 * Needs `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object ZioNativeStream extends ZIOAppDefault {

  // zio-http's default TLS configuration trusts any certificate, and its default pool opens 10
  // connections per host up front: verify against the JVM's trust store, grow the pool on demand
  private val client: ZLayer[Any, Throwable, Client] =
    ZLayer.succeed(
      ZClient.Config.default
        .ssl(ClientSSLConfig.FromSystemTrustStore)
        .dynamicConnectionPool(minimum = 1, maximum = 64, ttl = 60.seconds)
    ) >>> Client.fromConfig

  // the service on the engine, closed with the layer's scope (the engine is the layer's)
  private val openAI: ZLayer[ZioHttpWSClientEngine, Throwable, OpenAIStreamedServiceZIO] =
    ZLayer.scoped(
      for {
        engine <- ZIO.service[ZioHttpWSClientEngine]
        executor <- ZIO.executor
        runtime <- ZIO.runtime[Any]
        service <- ZIO.acquireRelease(
          ZIO.attempt(
            OpenAIStreamedServiceZIO(
              OpenAIServiceFactory.withStreaming.withEngine(engine)(
                executor.asExecutionContext
              )
            )(runtime)
          )
        )(_.close)
      } yield service
    )

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO
      .serviceWithZIO[OpenAIStreamedServiceZIO](
        _.createChatCompletionStreamed(
          messages = Seq(
            SystemMessage("You are a helpful assistant."),
            UserMessage("Name three Norwegian fjords.")
          ),
          settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
        ).map(_.contentHead.getOrElse("")).runForeach(text => Console.print(text))
      )
      .provide(client, ZioHttpWSClientEngine.layer(), openAI) *> Console.printLine("")
}
