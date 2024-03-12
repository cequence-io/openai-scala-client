package io.cequence.openaiscala.anthropic.service
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.openaiscala.ConfigImplicits.ConfigExt
import io.cequence.openaiscala.anthropic.service.impl.{
  AnthropicServiceImpl,
  OpenAIAnthropicChatCompletionService
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object AnthropicServiceFactory extends AnthropicServiceConsts {

  private class AnthropicServiceClassImpl(
    val coreUrl: String,
    val authHeaders: Seq[(String, String)]
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicServiceImpl {
    override protected val extraParams: Seq[(String, String)] = Nil
    override protected val explTimeouts: Option[Timeouts] = None
  }

  def withOpenAIAdapter(
    apiKey: String
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory.apply(apiKey)
    )

  def apply(
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService =
    apply(ConfigFactory.load(configFileName))

  def apply(
    config: Config
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    apply(apiKey = config.getString(s"$configPrefix.apiKey"))
  }

  def apply(
    apiKey: String
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val authHeaders = Seq(
      ("x-api-key", s"$apiKey"),
      ("anthropic-version", "2023-06-01")
    )
    new AnthropicServiceClassImpl(defaultCoreUrl, authHeaders)
  }

}
