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
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai")),
      owns = false
    )

  /** The engine was created for this service (e.g. by `forBedrockSigV4`), so it closes it. */
  override protected def ownedEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext
  )(
    implicit ec: ExecutionContext
  ): OpenAIService =
    new OpenAIServiceEngineImpl(
      engine,
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai")),
      owns = true
    )

  private final class OpenAIServiceEngineImpl(
    protected val engine: WSClientEngine,
    protected val site: SiteBinding,
    owns: Boolean
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAIServiceImpl {
    // a caller-supplied engine is closed by its creator; one built for this service is ours
    override protected def ownsEngine: Boolean = owns
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
