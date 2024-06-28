package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.wsclient.domain.WsRequestContext

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
    val coreUrl: String,
    override val requestContext: WsRequestContext
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAIChatCompletionServiceImpl
}

// propose a new name for the trait
trait IOpenAIChatCompletionServiceFactory[F] extends RawWsServiceFactory[F] {
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
        authHeaders = scala.collection.immutable.Seq(("Authorization", s"Bearer $accessToken"))
      )
    )
}
