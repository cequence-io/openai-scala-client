package io.cequence.openaiscala.examples

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.ProviderSettings
import io.cequence.openaiscala.perplexity.service.SonarServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.ExecutionContext

object ChatCompletionProvider {

  /**
   * Requires `CEREBRAS_API_KEY`
   */
  def cerebras(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.cerebras)

  /**
   * Requires `GROQ_API_KEY`
   */
  def groq(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.groq)

  /**
   * Requires `FIREWORKS_API_KEY`
   */
  def fireworks(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.fireworks)

  /**
   * Requires `MISTRAL_API_KEY`
   */
  def mistral(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.mistral)

  /**
   * Requires `OCTOAI_TOKEN`
   */
  def octoML(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.octoML)

  /**
   * Requires `TOGETHERAI_API_KEY`
   */
  def togetherAI(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.togetherAI)

  /**
   * Requires `GROK_API_KEY`
   */
  def grok(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.grok)

  /**
   * Requires `VERTEXAI_API_KEY` and "VERTEXAI_LOCATION"
   */
  def vertexAI(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService =
    VertexAIServiceFactory.asOpenAI()

  /**
   * Requires `ANTHROPIC_API_KEY`
   */
  def anthropic(
    withCache: Boolean = false
  )(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.asOpenAI(withCache = withCache)

  /**
   * Requires `ANTHROPIC_API_KEY`
   */
  def anthropicBedrock(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.bedrockAsOpenAI()

  def deepseek(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.deepseek)

  def deepseekBeta(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.deepseekBeta)

  def sonar(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService =
    SonarServiceFactory.asOpenAI()

  /**
   * Requires `GOOGLE_API_KEY`
   */
  def gemini(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.gemini)

  /**
   * Requires `NOVITA_API_KEY`
   */
  def novita(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(ChatProviderSettings.novita)

  private def provide(
    settings: ProviderSettings
  )(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService =
    OpenAIChatCompletionServiceFactory.withStreaming(settings)
}
