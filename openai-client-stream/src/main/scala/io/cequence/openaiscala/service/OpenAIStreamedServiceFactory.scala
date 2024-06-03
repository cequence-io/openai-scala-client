package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAICoreServiceStreamedExtraImpl
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.ExecutionContext

object OpenAIStreamedServiceFactory
    extends OpenAIServiceFactoryHelper[OpenAIStreamedServiceExtra] {

  override def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIStreamedServiceExtra =
    new OpenAICoreStreamedServiceExtraClassImpl(coreUrl, requestContext)

  private final class OpenAICoreStreamedServiceExtraClassImpl(
    val coreUrl: String,
    override val requestContext: WsRequestContext
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAICoreServiceStreamedExtraImpl
}
