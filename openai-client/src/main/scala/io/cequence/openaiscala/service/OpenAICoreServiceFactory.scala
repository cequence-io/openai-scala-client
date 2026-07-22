package io.cequence.openaiscala.service

import io.cequence.openaiscala.service.impl.OpenAICoreServiceImpl
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAICoreServiceFactory extends RawWsServiceFactory[OpenAICoreService] {

  override def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAICoreService =
    new OpenAICoreServiceClassImpl(coreUrl, requestContext, timeouts)

  override def withEngine(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): OpenAICoreService =
    new OpenAICoreServiceEngineImpl(
      engine,
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
    )

  private final class OpenAICoreServiceEngineImpl(
    protected val engine: WSClientEngine,
    protected val site: SiteBinding
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAICoreServiceImpl {
    // the engine is shared/caller-supplied - closed by its creator, not by this service
    override protected def ownsEngine: Boolean = false
  }

  private final class OpenAICoreServiceClassImpl(
    coreUrl: String,
    requestContext: WsRequestContext,
    timeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAICoreServiceImpl {
    // a private classpath-discovered engine, owned (and closed) by this service
    protected val engine: WSClientEngine =
      ProjectWSClientEngine(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))

    protected val site: SiteBinding =
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
  }
}
