package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.impl.OpenAIServiceImpl
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAIServiceFactory
    extends OpenAIServiceFactoryHelper[OpenAIService]
    with OpenAIServiceConsts {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    extraParams: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIService =
    new OpenAIServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
}

private class OpenAIServiceClassImpl(
  val coreUrl: String,
  val authHeaders: Seq[(String, String)],
  val extraParams: Seq[(String, String)],
  val explTimeouts: Option[Timeouts]
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends OpenAIServiceImpl
