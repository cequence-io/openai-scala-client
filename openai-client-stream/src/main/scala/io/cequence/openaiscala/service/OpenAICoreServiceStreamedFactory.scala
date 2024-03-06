package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts
import StreamedServiceTypes.OpenAICoreStreamedService

import scala.concurrent.ExecutionContext

object OpenAICoreServiceStreamedFactory
    extends OpenAIServiceFactoryHelper[OpenAICoreStreamedService] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAICoreStreamedService =
    new OpenAICoreServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
      with OpenAIServiceStreamedExtraImpl
}
