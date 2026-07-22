package io.cequence.openaiscala.service

import io.cequence.openaiscala.service.impl.OpenAIServiceImpl
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIServiceFactory
    extends OpenAIServiceFactoryHelper[OpenAIService]
    with OpenAIServiceConsts {

  override def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIService =
    new OpenAIServiceClassImpl(coreUrl, requestContext, timeouts)

  override def customEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): OpenAIService =
    new OpenAIServiceEngineImpl(
      engine,
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai"))
    )

  private final class OpenAIServiceEngineImpl(
    protected val engine: WSClientEngine,
    protected val site: SiteBinding
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAIServiceImpl {
    // the engine is shared/caller-supplied - closed by its creator, not by this service
    override protected def ownsEngine: Boolean = false
  }
}

private class OpenAIServiceClassImpl(
  coreUrl: String,
  requestContext: WsRequestContext,
  timeouts: Option[Timeouts] = None
)(
  implicit val ec: ExecutionContext
) extends OpenAIServiceImpl {
  // a private classpath-discovered engine, owned (and closed) by this service
  protected val engine: WSClientEngine =
    ProjectWSClientEngine(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))

  protected val site: SiteBinding =
    ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai"))
}
