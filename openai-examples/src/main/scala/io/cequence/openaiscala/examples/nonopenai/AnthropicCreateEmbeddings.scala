package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateEmbeddingsSettings
import io.cequence.openaiscala.anthropic.service.impl.DefaultSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.anthropic.domain.response.EmbeddingResponse
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateEmbeddings extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  val messages: Seq[String] = Seq("The quick brown fox jumps over the lazy dog")

  override protected def run: Future[_] =
    service
      .createEmbeddings(
        messages,
        settings = AnthropicCreateEmbeddingsSettings(
          model = DefaultSettings.CreateEmbeddings.model,
          input_type = Some("passage")
        )
      )
      .map(printEmbeddingContent)

  private def printEmbeddingContent(response: EmbeddingResponse) = {
    println(response.toString)
  }
}
