package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAICoreServiceImpl
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.ExecutionContext

object OpenAICoreServiceFactory extends RawWsServiceFactory[OpenAICoreService] {

  override def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAICoreService =
    new OpenAICoreServiceClassImpl(coreUrl, requestContext)

  private final class OpenAICoreServiceClassImpl(
    val coreUrl: String,
    override val requestContext: WsRequestContext
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAICoreServiceImpl
}
