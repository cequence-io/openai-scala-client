package io.cequence.openaiscala.anthropic.service

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.impl.{
  AnthropicServiceImpl,
  OpenAIAnthropicChatCompletionService
}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.domain.{RichResponse, WsRequestContext}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.service.{WSClientEngine, WSClientEngineStreamExtra}

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.ExecutionContext

/**
 * Factory for creating instances of the [[AnthropicService]] and an OpenAI adapter for
 * [[OpenAIChatCompletionService]]
 */
object AnthropicServiceFactory extends AnthropicServiceConsts {

  private def apiVersion = "2023-06-01"
  private def envAPIKey = "ANTHROPIC_API_KEY"

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
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val authHeaders = Seq(
      ("x-api-key", s"$apiKey"),
      ("anthropic-version", apiVersion)
    ) ++ (if (withPdf) Seq(("anthropic-beta", "pdfs-2024-09-25")) else Seq.empty) ++
      (if (withCache) Seq(("anthropic-beta", "prompt-caching-2024-07-31")) else Seq.empty)

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
    // Play WS engine
    override protected val engine: WSClientEngine with WSClientEngineStreamExtra =
      PlayWSStreamClientEngine(
        coreUrl,
        WsRequestContext(authHeaders = authHeaders, explTimeouts = explTimeouts)
      )
  }

  private def recoverErrors: String => PartialFunction[Throwable, RichResponse] = {
    (serviceEndPointName: String) =>
      {
        case e: TimeoutException =>
          throw new AnthropicScalaClientTimeoutException(
            s"${serviceEndPointName} timed out: ${e.getMessage}."
          )
        case e: UnknownHostException =>
          throw new AnthropicScalaClientUnknownHostException(
            s"${serviceEndPointName} cannot resolve a host name: ${e.getMessage}."
          )
      }
  }
}
