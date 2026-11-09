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
}
