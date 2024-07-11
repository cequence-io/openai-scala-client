package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIServiceImpl
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.WSClientEngine

import scala.concurrent.ExecutionContext

object OpenAIServiceFactory
    extends OpenAIServiceFactoryHelper[OpenAIService]
    with OpenAIServiceConsts {

  override def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIService =
    new OpenAIServiceClassImpl(coreUrl, requestContext)
}

private class OpenAIServiceClassImpl(
  coreUrl: String,
  requestContext: WsRequestContext
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends OpenAIServiceImpl {
  // we use play ws client engine
  protected val engine: WSClientEngine = ProjectWSClientEngine(coreUrl, requestContext)
}
