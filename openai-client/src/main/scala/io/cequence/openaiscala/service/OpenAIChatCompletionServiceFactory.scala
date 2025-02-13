package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.domain.ProviderSettings
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.WSClientEngine

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionServiceFactory
    extends IOpenAIChatCompletionServiceFactory[OpenAIChatCompletionService] {

  override def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceClassImpl(coreUrl, requestContext)

  private final class OpenAIChatCompletionServiceClassImpl(
    coreUrl: String,
    requestContext: WsRequestContext
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAIChatCompletionServiceImpl {
    // we use play ws client engine
    protected val engine: WSClientEngine = ProjectWSClientEngine(coreUrl, requestContext)
  }
}

// propose a new name for the trait
trait IOpenAIChatCompletionServiceFactory[F] extends RawWsServiceFactory[F] {

  def apply(
    providerSettings: ProviderSettings
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F =
    apply(
      coreUrl = providerSettings.coreUrl,
      WsRequestContext(authHeaders =
        Seq(
          ("Authorization", s"Bearer ${sys.env(providerSettings.apiKeyEnvVariable)}"),
        )
      )
    )

  def forAzureAI(
    endpoint: String,
    region: String,
    accessToken: String
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F =
    apply(
      coreUrl = s"https://${endpoint}.${region}.inference.ai.azure.com/v1/",
      requestContext = WsRequestContext(
        authHeaders = Seq(("Authorization", s"Bearer $accessToken"))
      )
    )
}
