package io.cequence.openaiscala.anthropic.service
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.impl.{AnthropicServiceImpl, OpenAIAnthropicChatCompletionService}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

object AnthropicServiceFactory extends AnthropicServiceConsts {

  private class AnthropicServiceClassImpl(
    val coreUrl: String,
    val authHeaders: Seq[(String, String)],
    val explTimeouts: Option[Timeouts]
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicServiceImpl

  def withOpenAIAdapter(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory.apply(apiKey, orgId, timeouts)
    )

  def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))

    val authHeaders = orgIdHeader ++: Seq(
      ("x-api-key", s"$apiKey"),
      ("anthropic-version", "2023-06-01")
      // ("OpenAI-Beta", "assistants=v1")
    )

    new AnthropicServiceClassImpl(defaultCoreUrl, authHeaders, timeouts)
  }

}
