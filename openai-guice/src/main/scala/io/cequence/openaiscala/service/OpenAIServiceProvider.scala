package io.cequence.openaiscala.service

import akka.stream.Materializer
import com.typesafe.config.Config

import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

private class OpenAIServiceProvider @Inject()(
  config: Config)(
  implicit ec: ExecutionContext, materializer: Materializer
) extends Provider[OpenAIService] {

  override def get: OpenAIService = OpenAIServiceFactory(config)
}