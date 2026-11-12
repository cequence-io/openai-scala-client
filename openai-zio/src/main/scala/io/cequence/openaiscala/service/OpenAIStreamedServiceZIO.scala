package io.cequence.openaiscala.service

import zio.{Runtime, ZIO, ZLayer}

/**
 * The ZIO view of the merged sync + streamed OpenAI service
 * (`OpenAIServiceFactory.withStreaming()`): every call as a `Task`, every stream as a
 * `ZStream`.
 *
 * {{{
 * val layer = OpenAIStreamedServiceZIO.layer(() => OpenAIServiceFactory.withStreaming())
 *
 * ZIO.serviceWithZIO[OpenAIStreamedServiceZIO](
 *   _.createChatCompletionStreamedTyped(messages).runForeach(chunk => Console.printLine(chunk))
 * ).provide(layer)
 * }}}
 */
trait OpenAIStreamedServiceZIO extends OpenAIServiceZIO with OpenAIStreamedServiceExtraZIO {

  override def asFuture: OpenAIService with OpenAIStreamedServiceExtra
}

object OpenAIStreamedServiceZIO {

  /** The ZIO view of `service`, run on `runtime`. */
  def apply(
    service: OpenAIService with OpenAIStreamedServiceExtra
  )(
    implicit runtime: Runtime[Any] = Runtime.default
  ): OpenAIStreamedServiceZIO =
    new OpenAIStreamedServiceZIO
      with OpenAIServiceZIODelegate
      with OpenAIStreamedServiceExtraZIODelegate {
      override val asFuture: OpenAIService with OpenAIStreamedServiceExtra = service
      override val zioRuntime: Runtime[Any] = runtime
    }

  /**
   * A layer of the ZIO view of the service `create` builds (on acquisition), closed with the
   * layer's scope (a function, not a by-name parameter: ZIO's `provide` macro cannot take a
   * by-name call).
   */
  def layer(
    create: () => OpenAIService with OpenAIStreamedServiceExtra
  ): ZLayer[Any, Throwable, OpenAIStreamedServiceZIO] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        ZIO.runtime[Any].flatMap(runtime => ZIO.attempt(apply(create())(runtime)))
      )(_.close)
    )

  /**
   * A layer of the ZIO view of the service the effect `create` builds (on acquisition) - e.g.
   * on an engine from the environment - closed with the layer's scope:
   * {{{
   * OpenAIStreamedServiceZIO.layerFromZIO(
   *   for {
   *     engine <- ZIO.service[ZioHttpWSClientEngine]
   *     executor <- ZIO.executor
   *   } yield OpenAIServiceFactory.withStreaming.withEngine(engine)(executor.asExecutionContext)
   * )
   * }}}
   */
  def layerFromZIO[R](
    create: ZIO[R, Throwable, OpenAIService with OpenAIStreamedServiceExtra]
  ): ZLayer[R, Throwable, OpenAIStreamedServiceZIO] =
    ZLayer.scoped[R](
      ZIO.acquireRelease(
        create.flatMap(service => ZIO.runtime[Any].map(apply(service)(_)))
      )(_.close)
    )
}
