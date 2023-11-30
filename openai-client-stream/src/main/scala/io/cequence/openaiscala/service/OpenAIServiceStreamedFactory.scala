package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIServiceStreamedFactory
    extends OpenAIServiceFactoryHelper[
      OpenAIService with OpenAIServiceStreamedExtra
    ] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIService with OpenAIServiceStreamedExtra =
    new OpenAIServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
      with OpenAIServiceStreamedExtraImpl
}
