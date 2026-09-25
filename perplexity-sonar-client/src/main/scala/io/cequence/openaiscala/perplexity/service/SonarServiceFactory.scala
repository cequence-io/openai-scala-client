package io.cequence.openaiscala.perplexity.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.perplexity.service.impl.{
  OpenAISonarChatCompletionService,
  PerplexityResponsesServiceImpl,
  SonarServiceImpl
}
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}

import scala.concurrent.ExecutionContext

/**
 * Factory for creating instances of the [[SonarService]] (Perplexity's Agent API and the
 * retiring Sonar chat completions API) and an OpenAI adapter for
 * [[io.cequence.openaiscala.service.OpenAIChatCompletionService]]. The API key comes from
 * `PERPLEXITY_API_KEY` (the name Perplexity's docs use) or `SONAR_API_KEY`.
 */
object SonarServiceFactory extends SonarServiceConsts with EnvHelper {

  private val apiKeyEnv = ChatProviderSettings.sonar.apiKeyEnvVariable

  private def defaultApiKey: String =
    Option(System.getenv("PERPLEXITY_API_KEY"))
      .filter(_.nonEmpty)
      .getOrElse(getEnvValue(apiKeyEnv))

  /**
   * @param baseUrl
   *   the API base URL (default `https://api.perplexity.ai/`), e.g. for a proxy
   */
  def apply(
    apiKey: String = defaultApiKey,
    baseUrl: Option[String] = None
  )(
    implicit ec: ExecutionContext
  ): SonarService = new SonarServiceImpl(apiKey, None, baseUrl)

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
    apiKey: String = defaultApiKey,
    baseUrl: Option[String] = None
  )(
    implicit ec: ExecutionContext
  ): SonarService = new SonarServiceImpl(apiKey, Some(engine), baseUrl)

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the SonarService -
   * on the Sonar chat completions API, which Perplexity supports only until 2026-09-27
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the PERPLEXITY_API_KEY or
   *   SONAR_API_KEY env. variable will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @return
   */
  @deprecated(
    "Built on the Sonar chat completions API, which Perplexity supports only until 2026-09-27 - use agentAsOpenAI (or the Agent API: createAgentResponse)",
    "1.3.1"
  )
  def asOpenAI(
    apiKey: String = defaultApiKey
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService =
    new OpenAISonarChatCompletionService(
      new SonarServiceImpl(apiKey)
    )

  /**
   * An OpenAI chat-completion service on Perplexity's Agent API - chat completions, function
   * tools (`createChatToolCompletion`), JSON (`createChatCompletionWithJSON`) and the typed
   * stream (`createChatToolCompletionStreamed`). Perplexity accepts `POST /v1/responses` as an
   * alias of `/v1/agent` for OpenAI SDK compatibility, so this is a Responses API client for
   * that alias behind the library's Responses chat adapter.
   *
   * Use a `provider/model` id as the model (e.g. `openai/gpt-5.4-mini`,
   * `anthropic/claude-sonnet-4-6` - see `listAgentModels`); presets, profiles and Perplexity's
   * built-in tools other than web search are available on the native [[SonarService]] only.
   * Web search: `settings.setResponsesTools(Seq(WebSearchTool()))`. Errors are the shared
   * `OpenAIScala*` exceptions with the [[PerplexityScalaClientException]] (error type, request
   * id) as the cause.
   *
   * @param baseUrl
   *   the API base URL (default `https://api.perplexity.ai/`), e.g. for a proxy
   */
  def agentAsOpenAI(
    apiKey: String = defaultApiKey,
    baseUrl: Option[String] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra = {
    OpenAIResponsesChatCompletionService(
      new PerplexityResponsesServiceImpl(apiKey, baseUrl.getOrElse(coreUrl))
    )
  }
}
