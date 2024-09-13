package io.cequence.openaiscala.examples

import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory,
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIChatCompletionStreamedServiceFactory
}
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.ExecutionContext

object ChatCompletionProvider {
  case class ProviderSettings(
    coreUrl: String,
    apiKeyEnvVariable: String
  )

  val Cerebras = ProviderSettings("https://api.cerebras.ai/v1/", "CEREBRAS_API_KEY")
  val Groq = ProviderSettings("https://api.groq.com/openai/v1/", "GROQ_API_KEY")
  val Fireworks =
    ProviderSettings("https://api.fireworks.ai/inference/v1/", "FIREWORKS_API_KEY")
  val Mistral = ProviderSettings("https://api.mistral.ai/v1/", "MISTRAL_API_KEY")
  val OctoML = ProviderSettings("https://text.octoai.run/v1/", "OCTOAI_TOKEN")
  val TogetherAI = ProviderSettings("https://api.together.xyz/v1/", "TOGETHERAI_API_KEY")

  def cerebras(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = provide(Cerebras)

  def groq(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = provide(Groq)

  def fireworks(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = provide(Fireworks)

  def mistral(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = provide(Mistral)

  def octoML(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = provide(OctoML)

  def togetherAI(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = provide(TogetherAI)

  def vertexAI(
    implicit ec: ExecutionContext,
    m: Materializer
  ) = VertexAIServiceFactory.asOpenAI()

  def anthropic(
    implicit ec: ExecutionContext,
    m: Materializer
  ) = AnthropicServiceFactory.asOpenAI()

  object streamed {
    def cerebras(
      implicit ec: ExecutionContext,
      m: Materializer
    ): OpenAIChatCompletionStreamedServiceExtra = provideStreamed(Cerebras)

    def groq(
      implicit ec: ExecutionContext,
      m: Materializer
    ): OpenAIChatCompletionStreamedServiceExtra = provideStreamed(Groq)

    def fireworks(
      implicit ec: ExecutionContext,
      m: Materializer
    ): OpenAIChatCompletionStreamedServiceExtra = provideStreamed(Fireworks)

    def mistral(
      implicit ec: ExecutionContext,
      m: Materializer
    ): OpenAIChatCompletionStreamedServiceExtra = provideStreamed(Mistral)

    def octoML(
      implicit ec: ExecutionContext,
      m: Materializer
    ): OpenAIChatCompletionStreamedServiceExtra = provideStreamed(OctoML)

    def togetherAI(
      implicit ec: ExecutionContext,
      m: Materializer
    ): OpenAIChatCompletionStreamedServiceExtra = provideStreamed(TogetherAI)
  }

  private def provide(
    settings: ProviderSettings
  )(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionService = OpenAIChatCompletionServiceFactory(
    coreUrl = settings.coreUrl,
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env(settings.apiKeyEnvVariable)}"))
    )
  )

  private def provideStreamed(
    settings: ProviderSettings
  )(
    implicit ec: ExecutionContext,
    m: Materializer
  ): OpenAIChatCompletionStreamedServiceExtra = OpenAIChatCompletionStreamedServiceFactory(
    coreUrl = settings.coreUrl,
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env(settings.apiKeyEnvVariable)}"))
    )
  )

}
