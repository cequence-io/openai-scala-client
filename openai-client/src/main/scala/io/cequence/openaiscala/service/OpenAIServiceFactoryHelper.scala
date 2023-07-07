package io.cequence.openaiscala.service

import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.openaiscala.service.ws.Timeouts
import io.cequence.openaiscala.ConfigImplicits._

import scala.concurrent.ExecutionContext

trait OpenAIServiceFactoryHelper[F] extends OpenAIServiceConsts {

  def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F

  def apply(
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F =
    apply(ConfigFactory.load(configFileName))

  def apply(
    config: Config
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): F = {
    def intTimeoutAux(fieldName: String) =
      config.optionalInt(s"$configPrefix.timeouts.${fieldName}Sec").map(_ * 1000)

    val timeouts = Timeouts(
      requestTimeout = intTimeoutAux("requestTimeout"),
      readTimeout = intTimeoutAux("readTimeout"),
      connectTimeout = intTimeoutAux("connectTimeout"),
      pooledConnectionIdleTimeout = intTimeoutAux("pooledConnectionIdleTimeout")
    )

    apply(
      apiKey = config.getString(s"$configPrefix.apiKey"),
      orgId = config.optionalString(s"$configPrefix.orgId"),
      timeouts =
        if (
          timeouts.requestTimeout.isDefined
          || timeouts.readTimeout.isDefined
          || timeouts.connectTimeout.isDefined
          || timeouts.pooledConnectionIdleTimeout.isDefined
        )
          Some(timeouts)
        else
          None
    )
  }
}
