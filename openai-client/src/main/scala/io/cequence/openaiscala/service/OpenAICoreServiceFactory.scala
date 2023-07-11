package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object OpenAICoreServiceFactory {

  def apply(
    coreUrl: String,
    authHeaders: Seq[(String, String)] = Nil,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAICoreService =
    new OpenAICoreServiceClassImpl(coreUrl, authHeaders, timeouts)
}

private class OpenAICoreServiceClassImpl(
  val coreUrl: String,
  val authHeaders: Seq[(String, String)],
  val explTimeouts: Option[Timeouts]
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends OpenAICoreServiceImpl
