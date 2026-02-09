package io.cequence.openaiscala.examples.googlevertexai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.vertexai.domain.Tool
import io.cequence.openaiscala.vertexai.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.Future

/**
 * Example showing Google Search grounding with Vertex AI.
 *
 * The model will use Google Search to find up-to-date information to answer queries.
 *
 * Requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and
 * `VERTEXAI_PROJECT_ID` environment variables to be set.
 */
object GoogleVertexAICreateChatCompletionWithToolsGoogleSearch
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = VertexAIServiceFactory.asOpenAI()

  private val model = NonOpenAIModelId.gemini_2_5_flash

  private val tools = Seq(Tool.GoogleSearch)

  private val messages = Seq(
    UserMessage("What are the latest news about AI developments today?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model,
          temperature = Some(0.7),
          max_tokens = Some(2000)
        ).setVertexAITools(tools)
      )
      .map { response =>
        println("Response (with Google Search grounding):")
        println(response.contentHead)
        println("Finish reason: " + response.choices.head.finish_reason.getOrElse("N/A"))
        println("Usage        : " + response.usage.get)
      }
}
