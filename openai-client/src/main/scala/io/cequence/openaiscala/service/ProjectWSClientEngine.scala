package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.PlayWSClientEngine

import scala.concurrent.ExecutionContext

object ProjectWSClientEngine {

  def apply(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit materializer: Materializer,
    ec: ExecutionContext
  ): WSClientEngine =
    // Play WS engine
    PlayWSClientEngine(coreUrl, requestContext)
}
