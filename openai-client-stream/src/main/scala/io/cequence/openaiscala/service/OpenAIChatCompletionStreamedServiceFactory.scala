package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceStreamedExtraImpl
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.service.{WSClientEngine, WSClientEngineStreamExtra}

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
    coreUrl: String,
    requestContext: WsRequestContext
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAIChatCompletionServiceStreamedExtraImpl {
    // Play WS engine
    override protected val engine: WSClientEngine with WSClientEngineStreamExtra =
      PlayWSStreamClientEngine(coreUrl, requestContext)
  }
}
