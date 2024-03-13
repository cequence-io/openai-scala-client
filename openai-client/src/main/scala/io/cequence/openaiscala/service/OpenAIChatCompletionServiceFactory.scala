package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIChatCompletionServiceImpl
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionServiceFactory extends RawWsServiceFactory[OpenAIChatCompletionService] {

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
