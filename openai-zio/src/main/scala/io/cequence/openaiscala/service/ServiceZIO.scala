package io.cequence.openaiscala.service

import io.cequence.wsclient.service.CloseableService
import zio.{Runtime, UIO, ZIO}

/**
 * The base of every ZIO-native service view (`OpenAIServiceZIO`, ...): the runtime its
 * callbacks and stream builders run on, and `close`.
 */
trait ServiceZIO {

  /** The runtime the view runs callbacks (e.g. tool-approval decisions) on. */
  def zioRuntime: Runtime[Any]

  /** Closes the service behind the view (a no-op for a service without a lifecycle). */
  def close: UIO[Unit] = ZIO.unit
}

/** A ZIO view of a closeable service. */
trait CloseableServiceZIO extends ServiceZIO

/** Closes the Future-based service behind a view. */
trait CloseableServiceZIODelegate extends CloseableServiceZIO {

  def asFuture: CloseableService

  override def close: UIO[Unit] = ZIO.succeed(asFuture.close())
}
