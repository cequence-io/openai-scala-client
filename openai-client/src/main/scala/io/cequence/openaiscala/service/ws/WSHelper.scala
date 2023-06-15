package io.cequence.openaiscala.service.ws

import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

trait WSHelper {

  protected implicit val materializer: Materializer

  protected def timeouts: Timeouts

  // defaults in ms
  private object DefaultTimeouts {
    val readTimeout = 60000
    val requestTimeout = 60000
    val connectTimeout = 5000
    val pooledConnectionIdleTimeout = 60000
  }

  protected lazy val client: StandaloneWSClient = {
    import play.shaded.ahc.org.asynchttpclient._

    val asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(timeouts.connectTimeout.getOrElse(DefaultTimeouts.connectTimeout))
      .setReadTimeout(timeouts.readTimeout.getOrElse(DefaultTimeouts.readTimeout))
      .setPooledConnectionIdleTimeout(timeouts.pooledConnectionIdleTimeout.getOrElse(DefaultTimeouts.pooledConnectionIdleTimeout))
      .setRequestTimeout(timeouts.requestTimeout.getOrElse(DefaultTimeouts.requestTimeout))
      .build
    val asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
    val client = new StandaloneAhcWSClient(asyncHttpClient)

    // add a shutdown hook
    scala.sys.addShutdownHook(client.close())

    client
  }

  def close: Unit =
    client.close()
}