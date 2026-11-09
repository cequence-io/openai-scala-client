package io.cequence.openaiscala.testkit

import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamCore}
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}

/**
 * Engines for wire specs: resolved like the library resolves them (`-Dws-client.engine=...`
 * picks one; else the highest priority on the test classpath - the JDK engine by default), so
 * the engine matrix runs the same specs on every engine.
 */
object TestEngines {

  def streaming(
    settings: TransportSettings = TransportSettings()
  ): WSClientEngine with WSClientOutputStreamCore =
    WSClientEngineRegistry.outputStreamed(settings)

  def plain(settings: TransportSettings = TransportSettings()): WSClientEngine =
    WSClientEngineRegistry(settings)
}
