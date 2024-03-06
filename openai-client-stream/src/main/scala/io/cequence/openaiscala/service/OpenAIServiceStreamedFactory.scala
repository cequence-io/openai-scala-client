package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts
import StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.ExecutionContext

object OpenAIServiceStreamedFactory extends OpenAIServiceFactoryHelper[OpenAIStreamedService] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIStreamedService =
    new OpenAIServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
      with OpenAIServiceStreamedExtraImpl
}
