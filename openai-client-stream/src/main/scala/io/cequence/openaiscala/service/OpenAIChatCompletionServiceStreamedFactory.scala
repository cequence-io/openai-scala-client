package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts
import StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionServiceStreamedFactory
    extends OpenAIServiceFactoryHelper[OpenAIChatCompletionStreamedService] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIChatCompletionServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
      with OpenAIChatCompletionServiceStreamedExtraImpl
}
