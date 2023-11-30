package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAICoreServiceStreamedFactory
    extends OpenAIServiceFactoryHelper[
      OpenAICoreService with OpenAIServiceStreamedExtra
    ] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAICoreService with OpenAIServiceStreamedExtra =
    new OpenAICoreServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
      with OpenAIServiceStreamedExtraImpl
}
