package io.cequence.openaiscala.anthropic.service

import akka.stream.Materializer
import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.anthropic.service.impl.{
  AnthropicBedrockServiceImpl,
  AnthropicServiceImpl,
  BedrockConnectionSettings,
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
object AnthropicServiceFactory extends AnthropicServiceConsts with EnvHelper {

  private def apiVersion = "2023-06-01"

  object EnvKeys {
    val anthropicAPIKey = "ANTHROPIC_API_KEY"
    val bedrockAccessKey = "AWS_BEDROCK_ACCESS_KEY"
    val bedrockSecretKey = "AWS_BEDROCK_SECRET_KEY"
    val bedrockRegion = "AWS_BEDROCK_REGION"
  }

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
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory(apiKey, timeouts, withPdf = false, withCache)
    )

  def bedrockAsOpenAI(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIAnthropicChatCompletionService(
      AnthropicServiceFactory.forBedrock(accessKey, secretKey, region, timeouts)
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
    apiKey: String = getEnvValue(EnvKeys.anthropicAPIKey),
    timeouts: Option[Timeouts] = None,
    withPdf: Boolean = false,
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService = {
    val authHeaders = Seq(
      ("x-api-key", s"$apiKey"),
      ("anthropic-version", apiVersion),
      ("anthropic-beta", "output-128k-2025-02-19")
    ) ++ (if (withPdf) Seq(("anthropic-beta", "pdfs-2024-09-25")) else Seq.empty) ++
      (if (withCache) Seq(("anthropic-beta", "prompt-caching-2024-07-31")) else Seq.empty)

    new AnthropicServiceClassImpl(defaultCoreUrl, authHeaders, timeouts)
  }

  def forBedrock(
    accessKey: String = getEnvValue(EnvKeys.bedrockAccessKey),
    secretKey: String = getEnvValue(EnvKeys.bedrockSecretKey),
    region: String = getEnvValue(EnvKeys.bedrockRegion),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): AnthropicService =
    new AnthropicBedrockServiceClassImpl(
      BedrockConnectionSettings(accessKey, secretKey, region),
      timeouts
    )

  private class AnthropicServiceClassImpl(
    coreUrl: String,
    authHeaders: Seq[(String, String)],
    explTimeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicServiceImpl {
    // Play WS engine
    override protected val engine: WSClientEngine with WSClientEngineStreamExtra =
      PlayWSStreamClientEngine(
        coreUrl,
        WsRequestContext(authHeaders = authHeaders, explTimeouts = explTimeouts),
        recoverErrors
      )
  }

  private class AnthropicBedrockServiceClassImpl(
    override val connectionInfo: BedrockConnectionSettings,
    explTimeouts: Option[Timeouts] = None
  )(
    implicit val ec: ExecutionContext,
    val materializer: Materializer
  ) extends AnthropicBedrockServiceImpl {

    // Play WS engine
    override protected val engine: WSClientEngine with WSClientEngineStreamExtra =
      PlayWSStreamClientEngine(
        coreUrl = bedrockCoreUrl(connectionInfo.region),
        WsRequestContext(explTimeouts = explTimeouts),
        recoverErrors
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
