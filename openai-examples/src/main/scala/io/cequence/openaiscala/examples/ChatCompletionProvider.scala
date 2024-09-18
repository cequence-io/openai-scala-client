package io.cequence.openaiscala.examples

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.ExecutionContext
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

object ChatCompletionProvider {
  private case class ProviderSettings(
    coreUrl: String,
    apiKeyEnvVariable: String
  )

  private val Cerebras = ProviderSettings("https://api.cerebras.ai/v1/", "CEREBRAS_API_KEY")
  private val Groq = ProviderSettings("https://api.groq.com/openai/v1/", "GROQ_API_KEY")
  private val Fireworks =
    ProviderSettings("https://api.fireworks.ai/inference/v1/", "FIREWORKS_API_KEY")
  private val Mistral = ProviderSettings("https://api.mistral.ai/v1/", "MISTRAL_API_KEY")
  private val OctoML = ProviderSettings("https://text.octoai.run/v1/", "OCTOAI_TOKEN")
  private val TogetherAI = ProviderSettings("https://api.together.xyz/v1/", "TOGETHERAI_API_KEY")

  /**
   * Requires `CEREBRAS_API_KEY`
   */
  def cerebras(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(Cerebras)

  /**
   * Requires `GROQ_API_KEY`
   */
  def groq(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(Groq)

  /**
   * Requires `FIREWORKS_API_KEY`
   */
  def fireworks(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(Fireworks)

  /**
   * Requires `MISTRAL_API_KEY`
   */
  def mistral(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(Mistral)

  /**
   * Requires `OCTOAI_TOKEN`
   */
  def octoML(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(OctoML)

  /**
   * Requires `TOGETHERAI_API_KEY`
   */
  def togetherAI(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = provide(TogetherAI)

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
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService =
    AnthropicServiceFactory.asOpenAI()

  private def provide(
    settings: ProviderSettings
  )(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedService = OpenAIChatCompletionServiceFactory.withStreaming(
    coreUrl = settings.coreUrl,
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env(settings.apiKeyEnvVariable)}"))
    )
  )
}
