package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceStreamedExtraImpl
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionStreamedServiceFactory
    extends RawWsServiceFactory[OpenAIChatCompletionStreamedServiceExtra] {

  override def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedServiceExtraClassImpl(coreUrl, requestContext, timeouts)

  override def withEngine(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedServiceExtra =
    engine match {
      case streamed: WSClientOutputStreamExtraAkka =>
        new OpenAIChatCompletionStreamedServiceExtraEngineImpl(
          streamed,
          ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
        )
      case _ =>
        throw new OpenAIScalaClientException(
          "The streamed service factory requires an engine with Source-typed output streaming " +
            s"(WSClientOutputStreamExtraAkka) but got ${engine.getClass.getName}."
        )
    }

  private final class OpenAIChatCompletionStreamedServiceExtraEngineImpl(
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    protected val site: SiteBinding
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAIChatCompletionServiceStreamedExtraImpl {
    // the engine is shared/caller-supplied - closed by its creator, not by this service
    override protected def ownsEngine: Boolean = false
  }

  private final class OpenAIChatCompletionStreamedServiceExtraClassImpl(
    coreUrl: String,
    requestContext: WsRequestContext,
    timeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAIChatCompletionServiceStreamedExtraImpl {
    // a private classpath-discovered engine with output streaming (SSE) support, owned (and
    // closed) by this service
    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
      StreamedEngineRegistry.outputStreamed(
        TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
      )

    protected val site: SiteBinding =
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
  }
}
