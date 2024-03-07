package io.cequence.openaiscala.anthropic.service
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.impl.AnthropicServiceImpl
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object AnthropicServiceFactory extends AnthropicServiceFactoryHelper[AnthropicService] {

  override def customInstance(
    coreUrl: String,
    authHeaders: Seq[(String, String)],
    extraParams: Seq[(String, String)],
    timeouts: Option[Timeouts]
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService =
    new AnthropicServiceClassImpl(coreUrl, authHeaders, extraParams, timeouts)
}

private class AnthropicServiceClassImpl(
  val coreUrl: String,
  val authHeaders: Seq[(String, String)],
  val extraParams: Seq[(String, String)],
  val explTimeouts: Option[Timeouts]
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends AnthropicServiceImpl
