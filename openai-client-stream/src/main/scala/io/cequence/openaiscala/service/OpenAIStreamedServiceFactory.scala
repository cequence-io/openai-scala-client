package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts
import io.cequence.openaiscala.service.impl.OpenAICoreServiceStreamedExtraImpl

import scala.concurrent.ExecutionContext

object OpenAIStreamedServiceFactory
    extends OpenAIServiceFactoryHelper[OpenAIStreamedServiceExtra] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIStreamedServiceExtra =
    new OpenAICoreStreamedServiceExtraClassImpl(coreUrl, authHeaders, extraParams, timeouts)

  private final class OpenAICoreStreamedServiceExtraClassImpl(
    val coreUrl: String,
    val authHeaders: Seq[(String, String)],
    val extraParams: Seq[(String, String)],
    val explTimeouts: Option[Timeouts]
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAICoreServiceStreamedExtraImpl
}
