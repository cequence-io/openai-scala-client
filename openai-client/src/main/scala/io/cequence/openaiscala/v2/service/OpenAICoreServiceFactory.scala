package io.cequence.openaiscala.v2.service

import akka.stream.Materializer
import io.cequence.openaiscala.v2.service.impl.OpenAICoreServiceImpl
import io.cequence.openaiscala.v2.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAICoreServiceFactory extends RawWsServiceFactory[OpenAICoreService] {

  override def apply(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAICoreService =
    new OpenAICoreServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)

  private final class OpenAICoreServiceClassImpl(
    val coreUrl: String,
    val authHeaders: Seq[(String, String)],
    val extraParams: Seq[(String, String)],
    val explTimeouts: Option[Timeouts]
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends OpenAICoreServiceImpl
}
