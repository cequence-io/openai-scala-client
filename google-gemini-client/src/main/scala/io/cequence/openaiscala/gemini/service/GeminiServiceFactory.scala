package io.cequence.openaiscala.gemini.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.gemini.service.impl.{
  GeminiServiceImpl,
  OpenAIGeminiChatCompletionService
}
import io.cequence.openaiscala.service.{ChatProviderSettings, OpenAIChatCompletionBatchService}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.ExecutionContext
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

/**
 * Factory for creating instances of the [[GeminiService]] and an OpenAI adapter for
 * [[io.cequence.openaiscala.service.OpenAIChatCompletionService]]
 */
object GeminiServiceFactory extends GeminiServiceConsts with EnvHelper {

  private val apiKeyEnv = ChatProviderSettings.gemini.apiKeyEnvVariable

  def apply(
    apiKey: String = getEnvValue(apiKeyEnv),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): GeminiService = new GeminiServiceImpl(apiKey, timeouts)

  /**
   * Creates the service on a CALLER-SUPPLIED, SITE-STATELESS streaming engine - e.g. one
   * shared with other providers via `StreamedEngineRegistry.outputStreamed()` - so several
   * providers can share one connection pool and actor system. The site binding (base URL,
   * `key` auth query param, logging label) is built here from `apiKey` and held by the
   * service, threaded into every engine call. Closing such a service does NOT close the shared
   * engine - close the engine once, when done with all services using it.
   *
   * @param apiKey
   *   still required alongside the engine: the raw file upload/download paths bypass the
   *   engine and authenticate with the key directly - use the same key the site is built with
   */
  def withEngine(
    engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    apiKey: String = getEnvValue(apiKeyEnv)
  )(
    implicit ec: ExecutionContext
  ): GeminiService = new GeminiServiceImpl(apiKey, None, Some(engine))

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the SonarService
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the GOOGLE_API_KEY env. variable
   *   will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def asOpenAI(
    apiKey: String = getEnvValue(apiKeyEnv),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService =
    new OpenAIGeminiChatCompletionService(
      new GeminiServiceImpl(apiKey, timeouts)
    )

  /**
   * OpenAI-adapter wrapper for an EXISTING [[GeminiService]] - e.g. one created with
   * [[withEngine]] on a view bound to a shared transport.
   */
  def asOpenAI(
    service: GeminiService
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService with OpenAIChatCompletionBatchService =
    new OpenAIGeminiChatCompletionService(service)
}
