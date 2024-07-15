package io.cequence.openaiscala.service

import akka.stream.Materializer
import io.cequence.openaiscala.{OpenAIScalaClientTimeoutException, OpenAIScalaClientUnknownHostException}
import io.cequence.wsclient.domain.{RichResponse, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.PlayWSClientEngine

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
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
    PlayWSClientEngine(coreUrl, requestContext, recoverErrors)

  private def recoverErrors: String => PartialFunction[Throwable, RichResponse] = {
    serviceEndPointName: String =>
      {
        case e: TimeoutException =>
          throw new OpenAIScalaClientTimeoutException(
            s"${serviceEndPointName} timed out: ${e.getMessage}."
          )
        case e: UnknownHostException =>
          throw new OpenAIScalaClientUnknownHostException(
            s"${serviceEndPointName} cannot resolve a host name: ${e.getMessage}."
          )
      }
  }
}
