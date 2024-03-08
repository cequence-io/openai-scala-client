package io.cequence.openaiscala.anthropic.service.impl

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.{AnthropicServiceConsts, EndPoint, Param}
import io.cequence.openaiscala.service.ws.{Timeouts, WSRequestHelper}
import play.api.libs.ws.StandaloneWSRequest

import scala.concurrent.ExecutionContext

/**
 * Core WS stuff for OpenAI services.
 *
 * @since March
 *   2024
 */
private[service] trait AnthropicServiceWSHelper
    extends WSRequestHelper
    with AnthropicServiceConsts {

  override protected type PEP = EndPoint
  override protected type PT = Param

  protected implicit val ec: ExecutionContext
  protected implicit val materializer: Materializer

  protected val explTimeouts: Option[Timeouts]
  protected val authHeaders: Seq[(String, String)]
  protected val extraParams: Seq[(String, String)]

  override protected def timeouts: Timeouts =
    explTimeouts.getOrElse(
      Timeouts(
        requestTimeout = Some(defaultRequestTimeout),
        readTimeout = Some(defaultReadoutTimeout)
      )
    )

  // auth

  override protected def getWSRequestOptional(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])] = Nil
  ): StandaloneWSRequest#Self = {
    val extraStringParams = extraParams.map { case (tag, value) => (tag, Some(value)) }

    super
      .getWSRequestOptional(
        endPoint,
        endPointParam,
        params ++ extraStringParams
      )
      .addHttpHeaders(authHeaders: _*)
  }

  override protected def getWSRequest(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(String, Any)] = Nil
  ): StandaloneWSRequest#Self =
    super
      .getWSRequest(
        endPoint,
        endPointParam,
        params ++ extraParams
      )
      .addHttpHeaders(authHeaders: _*)
}
