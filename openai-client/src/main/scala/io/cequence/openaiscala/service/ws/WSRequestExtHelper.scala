package io.cequence.openaiscala.service.ws

import play.api.libs.ws.StandaloneWSRequest

/**
 * Core WS stuff for OpenAI services.
 *
 * @since March
 *   2024
 */
trait WSRequestExtHelper extends WSRequestHelper {

  protected val defaultRequestTimeout = 120 * 1000 // two minutes
  protected val defaultReadoutTimeout = 120 * 1000 // two minutes

  protected val explTimeouts: Option[Timeouts]
  protected val authHeaders: Seq[(String, String)]
  protected val extraParams: Seq[(String, String)]

  override protected def timeouts: Timeouts = {
    explTimeouts.getOrElse(
      Timeouts(
        requestTimeout = Some(defaultRequestTimeout),
        readTimeout = Some(defaultReadoutTimeout)
      )
    )
  }

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
