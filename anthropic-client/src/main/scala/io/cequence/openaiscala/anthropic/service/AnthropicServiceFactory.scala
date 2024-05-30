package io.cequence.openaiscala.anthropic.service

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.impl.{
  AnthropicServiceImpl,
  OpenAIAnthropicChatCompletionService
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

/**
 * Factory for creating instances of the [[AnthropicService]] and an OpenAI adapter for
 * [[OpenAIChatCompletionService]]
 */
object AnthropicServiceFactory extends AnthropicServiceConsts {

  private val apiVersion = "2023-06-01"
  private val envAPIKey = "ANTHROPIC_API_KEY"

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the AnthropicService
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @param materializer
   * @return
   */
  def asOpenAI(
    apiKey: String = getAPIKeyFromEnv(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory(apiKey, timeouts)
    )

  /**
   * Create a new instance of the [[AnthropicService]]
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the ANTHROPIC_API_KEY env.
   *   variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @param materializer
   * @return
   */
  def apply(
    apiKey: String = getAPIKeyFromEnv(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val authHeaders = Seq(
      ("x-api-key", s"$apiKey"),
      ("anthropic-version", apiVersion)
    )
    new AnthropicServiceClassImpl(defaultCoreUrl, authHeaders, timeouts)
  }

  private def getAPIKeyFromEnv(): String =
    Option(System.getenv(envAPIKey)).getOrElse(
      throw new IllegalStateException(
        "ANTHROPIC_API_KEY environment variable expected but not set. Alternatively, you can pass the API key explicitly to the factory method."
      )
    )

  private class AnthropicServiceClassImpl(
    val coreUrl: String,
    val authHeaders: Seq[(String, String)],
    val explTimeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicServiceImpl {
    override protected val requestContext: WsRequestContext =
      WsRequestContext(authHeaders = authHeaders, explTimeouts = explTimeouts)
  }
}
