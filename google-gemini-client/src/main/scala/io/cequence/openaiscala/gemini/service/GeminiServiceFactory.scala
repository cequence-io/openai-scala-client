package io.cequence.openaiscala.gemini.service

import akka.stream.Materializer
import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.gemini.service.impl.{
  GeminiServiceImpl,
  OpenAIGeminiChatCompletionService
}
import io.cequence.openaiscala.service.ChatProviderSettings
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.ExecutionContext

/**
 * Factory for creating instances of the [[GeminiService]] and an OpenAI adapter for
 * [[io.cequence.openaiscala.service.OpenAIChatCompletionService]]
 */
object GeminiServiceFactory extends GeminiServiceConsts with EnvHelper {

  private val apiKeyEnv = ChatProviderSettings.gemini.apiKeyEnvVariable

  def apply(
    apiKey: String = getEnvValue(apiKeyEnv)
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): GeminiService = new GeminiServiceImpl(apiKey)

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the SonarService
   *
   * @param apiKey
   *   The API key to use for authentication (if not specified the SONAR_API_KEY env. variable
   *   will be used)
   * @param timeouts
   *   The explicit timeouts to use for the service (optional)
   * @param ec
   * @param materializer
   * @return
   */
  def asOpenAI(
    apiKey: String = getEnvValue(apiKeyEnv)
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionStreamedService =
    new OpenAIGeminiChatCompletionService(
      new GeminiServiceImpl(apiKey)
    )
}
