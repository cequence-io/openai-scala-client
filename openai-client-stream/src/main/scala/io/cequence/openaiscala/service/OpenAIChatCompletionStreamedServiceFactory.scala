package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceStreamedExtraImpl
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionStreamedServiceFactory
    extends RawWsServiceFactory[OpenAIChatCompletionStreamedServiceExtra] {

  override def apply(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedServiceExtraClassImpl(
      coreUrl,
      authHeaders,
      extraParams,
      timeouts
    )

  private final class OpenAIChatCompletionStreamedServiceExtraClassImpl(
    val coreUrl: String,
    val authHeaders: Seq[(String, String)],
    val extraParams: Seq[(String, String)],
    val explTimeouts: Option[Timeouts]
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAIChatCompletionServiceStreamedExtraImpl
}
