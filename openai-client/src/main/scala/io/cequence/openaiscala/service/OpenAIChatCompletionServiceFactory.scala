package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.ProviderSettings
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionServiceFactory
    extends IOpenAIChatCompletionServiceFactory[OpenAIChatCompletionService] {

  override def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceClassImpl(coreUrl, requestContext, timeouts)

  override def withEngine(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceEngineImpl(
      engine,
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
    )

  private final class OpenAIChatCompletionServiceEngineImpl(
    protected val engine: WSClientEngine,
    protected val site: SiteBinding
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAIChatCompletionServiceImpl
      with HandleOpenAIErrorCodes {
    // the engine is shared/caller-supplied - closed by its creator, not by this service
    override protected def ownsEngine: Boolean = false
  }

  private final class OpenAIChatCompletionServiceClassImpl(
    coreUrl: String,
    requestContext: WsRequestContext,
    timeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext
  ) extends OpenAIChatCompletionServiceImpl
      with HandleOpenAIErrorCodes {
    // a private classpath-discovered engine, owned (and closed) by this service
    protected val engine: WSClientEngine =
      ProjectWSClientEngine(TransportSettings(timeouts = timeouts.getOrElse(Timeouts())))

    protected val site: SiteBinding =
      ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
  }
}

// propose a new name for the trait
trait IOpenAIChatCompletionServiceFactory[F] extends RawWsServiceFactory[F] {

  def apply(
    providerSettings: ProviderSettings
  )(
    implicit ec: ExecutionContext
  ): F =
    apply(
      coreUrl = providerSettings.coreUrl,
      WsRequestContext(authHeaders =
        Seq(
          ("Authorization", s"Bearer ${sys.env(providerSettings.apiKeyEnvVariable)}")
        )
      )
    )

  def forAzureAI(
    endpoint: String,
    region: String,
    accessToken: String
  )(
    implicit ec: ExecutionContext
  ): F =
    apply(
      coreUrl = s"https://${endpoint}.${region}.inference.ai.azure.com/v1/",
      requestContext = WsRequestContext(
        authHeaders = Seq(("Authorization", s"Bearer $accessToken"))
      )
    )

  /** Azure AI inference variant backed by a caller-owned shared engine. */
  def forAzureAIWithEngine(
    engine: WSClientEngine,
    endpoint: String,
    region: String,
    accessToken: String
  )(
    implicit ec: ExecutionContext
  ): F =
    withEngine(
      engine = engine,
      coreUrl = s"https://${endpoint}.${region}.inference.ai.azure.com/v1/",
      requestContext = WsRequestContext(
        authHeaders = Seq(("Authorization", s"Bearer $accessToken"))
      )
    )
}
