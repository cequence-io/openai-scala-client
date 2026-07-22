package io.cequence.openaiscala.perplexity.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.perplexity.service.impl.{
  OpenAISonarChatCompletionService,
  SonarServiceImpl
}
import io.cequence.openaiscala.service.ChatProviderSettings
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

import scala.concurrent.ExecutionContext

/**
 * Factory for creating instances of the [[SonarService]] and an OpenAI adapter for
 * [[io.cequence.openaiscala.service.OpenAIChatCompletionService]]
 */
object SonarServiceFactory extends SonarServiceConsts with EnvHelper {

  private val apiKeyEnv = ChatProviderSettings.sonar.apiKeyEnvVariable

  def apply(
    apiKey: String = getEnvValue(apiKeyEnv)
  )(
    implicit ec: ExecutionContext
  ): SonarService = new SonarServiceImpl(apiKey)

  /**
   * Creates the service on a CALLER-SUPPLIED, SITE-STATELESS streaming engine - e.g. one
   * shared with other providers via `StreamedEngineRegistry.outputStreamed()` - so several
   * providers can share one connection pool and actor system. The site binding (base URL,
   * Bearer auth header, logging label) is built here from `apiKey` and held by the service,
   * threaded into every engine call. Closing such a service does NOT close the shared engine -
   * close the engine once, when done with all services using it.
   */
  def withEngine(
    engine: WSClientEngine with WSClientOutputStreamExtraAkka,
    apiKey: String = getEnvValue(apiKeyEnv)
  )(
    implicit ec: ExecutionContext
  ): SonarService = new SonarServiceImpl(apiKey, Some(engine))

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the SonarService
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the SONAR_API_KEY env. variable
   *   will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  def asOpenAI(
    apiKey: String = getEnvValue(apiKeyEnv)
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService =
    new OpenAISonarChatCompletionService(
      new SonarServiceImpl(apiKey)
    )
}
