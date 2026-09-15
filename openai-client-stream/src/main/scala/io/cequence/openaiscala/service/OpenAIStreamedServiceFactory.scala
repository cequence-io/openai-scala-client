package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.service.impl.OpenAICoreServiceStreamedExtraImpl
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

import scala.concurrent.ExecutionContext

object OpenAIStreamedServiceFactory
    extends OpenAIServiceFactoryHelper[OpenAIStreamedServiceExtra] {

  override def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIStreamedServiceExtra =
    new OpenAICoreStreamedServiceExtraClassImpl(coreUrl, requestContext, timeouts)

  override def customEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): OpenAIStreamedServiceExtra =
    engine match {
      case streamed: WSClientOutputStreamExtraAkka =>
        new OpenAICoreStreamedServiceExtraEngineImpl(
          streamed,
          ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai"))
        )
      case _ =>
        throw new OpenAIScalaClientException(
          "The streamed service factory requires an engine with Source-typed output streaming " +
            s"(WSClientOutputStreamExtraAkka) but got ${engine.getClass.getName}."
        )
    }

  /** The engine was created for this service (e.g. by `forBedrock`), so it closes it. */
  override protected def ownedEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext
  )(
    implicit ec: ExecutionContext
  ): OpenAIStreamedServiceExtra =
    engine match {
      case streamed: WSClientOutputStreamExtraAkka =>
        new OpenAICoreStreamedServiceExtraEngineImpl(
          streamed,
          ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai")),
          owns = true
        )
      case _ =>
        throw new OpenAIScalaClientException(
          "The streamed service factory requires an engine with Source-typed output streaming " +
            s"(WSClientOutputStreamExtraAkka) but got ${engine.getClass.getName}."
        )
    }

  override protected def newPrivateEngine(
    timeouts: Option[Timeouts]
  )(
    implicit ec: ExecutionContext
  ): WSClientEngine =
    StreamedEngineRegistry.outputStreamed(
      TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
    )

  private final class OpenAICoreStreamedServiceExtraEngineImpl(
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    protected val site: SiteBinding,
    owns: Boolean = false
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAICoreServiceStreamedExtraImpl {
    // a caller-supplied engine is closed by its creator; one built for this service is ours
    override protected def ownsEngine: Boolean = owns
  }

  private final class OpenAICoreStreamedServiceExtraClassImpl(
    coreUrl: String,
    requestContext: WsRequestContext,
    timeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAICoreServiceStreamedExtraImpl {
    // a private classpath-discovered engine with output streaming (SSE) support, owned (and
    // closed) by this service
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
      StreamedEngineRegistry.outputStreamed(
        TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
      )

    protected val site: SiteBinding =
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext, label = Some("openai"))
  }
}
