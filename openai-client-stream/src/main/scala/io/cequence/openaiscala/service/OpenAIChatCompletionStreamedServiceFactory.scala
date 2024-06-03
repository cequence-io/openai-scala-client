package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceStreamedExtraImpl
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionStreamedServiceFactory
    extends RawWsServiceFactory[OpenAIChatCompletionStreamedServiceExtra] {

  override def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedServiceExtraClassImpl(coreUrl, requestContext)

  private final class OpenAIChatCompletionStreamedServiceExtraClassImpl(
    val coreUrl: String,
    override val requestContext: WsRequestContext
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAIChatCompletionServiceStreamedExtraImpl
}
