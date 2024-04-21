package io.cequence.openaiscala.v2.service

import akka.stream.Materializer
import io.cequence.openaiscala.v2.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.openaiscala.v2.service.ws.Timeouts

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
    val authHeaders: Seq[(String, String)],
    val extraParams: Seq[(String, String)],
    val explTimeouts: Option[Timeouts]
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
