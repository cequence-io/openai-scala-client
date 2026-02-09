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
 * Example showing code execution with Vertex AI.
 *
 * The model can generate and execute Python code to solve problems, returning both the code
 * and its execution result in the response.
 *
 * Requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and
 * `VERTEXAI_PROJECT_ID` environment variables to be set.
 */
object GoogleVertexAICreateChatCompletionWithToolsCodeExecution
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = VertexAIServiceFactory.asOpenAI()

  private val model = NonOpenAIModelId.gemini_2_5_flash

  private val tools = Seq(Tool.CodeExecution)

  private val messages = Seq(
    UserMessage("Calculate the first 20 Fibonacci numbers and show them in a formatted list.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model,
          temperature = Some(0.2),
          max_tokens = Some(2000)
        ).setVertexAITools(tools)
      )
      .map { response =>
        println("Response (with Code Execution):")
        println(response.contentHead)
        println("Finish reason: " + response.choices.head.finish_reason.getOrElse("N/A"))
        println("Usage        : " + response.usage.get)
      }
}
