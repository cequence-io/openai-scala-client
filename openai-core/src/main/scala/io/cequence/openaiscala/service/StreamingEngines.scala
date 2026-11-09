package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamCore}
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}

/**
 * Engines for streamed services: one with output streaming (`WSClientOutputStreamCore`) - jdk
 * (the default, part of openai-core), zio-http, play-akka-stream, play-pekko-stream and
 * pekko-http. sttp and the plain Play engines (play-akka, play-pekko) serve the non-streamed
 * calls only.
 */
object StreamingEngines {

  val StreamingEngineNames =
    "jdk (the default), zio-http, play-akka-stream, play-pekko-stream, pekko-http"

  val NonStreamingEngineNames = "sttp, play-akka, play-pekko"

  /**
   * A private engine with output streaming, resolved like every engine (`-Dws-client.engine`,
   * else the highest priority among the streaming engines on the classpath - so with play-akka
   * and jdk on the classpath, streams go through jdk).
   */
  def outputStreamed(
    settings: TransportSettings = TransportSettings()
  ): WSClientEngine with WSClientOutputStreamCore =
    WSClientEngineRegistry.outputStreamed(settings)

  /**
   * `engine` as a streaming engine, or an [[OpenAIScalaClientException]] naming the engines
   * that stream.
   */
  def require(
    engine: WSClientEngine,
    what: String = "A streamed service"
  ): WSClientEngine with WSClientOutputStreamCore =
    engine match {
      case streamed: WSClientOutputStreamCore =>
        streamed

      case _ =>
        throw new OpenAIScalaClientException(
          s"$what needs an engine with output streaming - $StreamingEngineNames - but got ${engine.getClass.getName} " +
            s"($NonStreamingEngineNames serve the non-streamed calls only)."
        )
    }
}
