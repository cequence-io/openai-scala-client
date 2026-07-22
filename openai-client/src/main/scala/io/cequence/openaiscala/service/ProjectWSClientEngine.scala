package io.cequence.openaiscala.service

import io.cequence.openaiscala.{
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException
}
import io.cequence.wsclient.domain.{
  CequenceWSTimeoutException,
  CequenceWSUnknownHostException,
  RichResponse,
  SiteBinding,
  WsRequestContext
}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ProjectWSClientEngine {

  /**
   * Creates a SITE-STATELESS engine via classpath self-discovery (`WSClientEngineRegistry`) -
   * the backend is whichever `ws-client` engine module is on the classpath (selectable
   * explicitly with `-Dws-client.engine=<id>`). The discovered engine owns its execution
   * environment, so no materializer or execution context is needed here. One engine serves any
   * number of sites/providers - pair it with a [[siteBinding]] per provider.
   */
  def apply(
    transportSettings: TransportSettings = TransportSettings()
  ): WSClientEngine =
    WSClientEngineRegistry(transportSettings)

  /**
   * The OpenAI-flavored site binding: base URL + request context plus the OpenAI exception
   * taxonomy in `recoverErrors` - held by the service and fed into every engine call.
   */
  def siteBinding(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext(),
    label: Option[String] = None
  ): SiteBinding =
    SiteBinding(
      coreUrl,
      requestContext,
      recoverErrors = Some(recoverErrors),
      label = label
    )

  private[service] def recoverErrors: String => PartialFunction[Throwable, RichResponse] = {
    (serviceEndPointName: String) =>
      {
        // the engine normalizes transport failures to the Cequence taxonomy before this
        // recovery is applied; the raw types are kept as a safety net
        case e @ (_: CequenceWSTimeoutException | _: TimeoutException) =>
          throw new OpenAIScalaClientTimeoutException(
            s"${serviceEndPointName} timed out: ${e.getMessage}."
          )
        case e @ (_: CequenceWSUnknownHostException | _: UnknownHostException) =>
          throw new OpenAIScalaClientUnknownHostException(
            s"${serviceEndPointName} cannot resolve a host name: ${e.getMessage}."
          )
      }
  }
}
