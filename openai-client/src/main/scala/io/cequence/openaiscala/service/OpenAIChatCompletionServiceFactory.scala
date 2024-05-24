package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionServiceFactory
    extends IOpenAIChatCompletionServiceFactory[OpenAIChatCompletionService] {

  override def apply(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)

  private final class OpenAIChatCompletionServiceClassImpl(
    val coreUrl: String,
    override val authHeaders: Seq[(String, String)],
    override val extraParams: Seq[(String, String)],
    override val explTimeouts: Option[Timeouts]
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
      authHeaders = Seq(("Authorization", s"Bearer $accessToken"))
    )
}
