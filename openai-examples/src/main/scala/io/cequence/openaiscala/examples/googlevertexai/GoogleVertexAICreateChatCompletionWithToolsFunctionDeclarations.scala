package io.cequence.openaiscala.examples.googlevertexai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.vertexai.domain.{FunctionDeclaration, Schema, SchemaType, Tool}
import io.cequence.openaiscala.vertexai.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.vertexai.domain.settings.{FunctionCallingMode, ToolConfig}
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.Future

/**
 * Example showing function calling with Vertex AI.
 *
 * Demonstrates using native tool format with function declarations through the Vertex AI
 * service.
 *
 * Requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and
 * `VERTEXAI_PROJECT_ID` environment variables to be set.
 */
object GoogleVertexAICreateChatCompletionWithToolsFunctionDeclarations
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = VertexAIServiceFactory.asOpenAI()

  private val model = NonOpenAIModelId.gemini_2_5_flash

  private val tools = Seq(
    Tool.FunctionDeclarations(
      Seq(
        FunctionDeclaration(
          name = "get_weather",
          description = "Get the current weather for a location.",
          parameters = Some(
            Schema(
              `type` = SchemaType.OBJECT,
              properties = Some(
                Map(
                  "location" -> Schema(
                    `type` = SchemaType.STRING,
                    description = Some("City, State or country")
                  ),
                  "unit" -> Schema(
                    `type` = SchemaType.STRING,
                    description = Some("Temperature unit"),
                    `enum` = Some(Seq("c", "f"))
                  )
                )
              ),
              required = Some(Seq("location"))
            )
          )
        )
      )
    )
  )

  private val toolConfig = ToolConfig.FunctionCallingConfig(
    mode = Some(FunctionCallingMode.ANY),
    allowedFunctionNames = Some(Seq("get_weather"))
  )

  private val messages = Seq(
    UserMessage("What is the weather in Oslo right now?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model,
          temperature = Some(0.7),
          max_tokens = Some(2000)
        ).setVertexAITools(tools).setVertexAIToolConfig(toolConfig)
      )
      .map { response =>
        println(response.contentHead)
        println("Finish reason: " + response.choices.head.finish_reason.getOrElse("N/A"))
        println("Usage        : " + response.usage.get)
        println(
          "\nNote: Function calls are embedded in the response. The model is requesting to call the get_weather function."
        )
      }
}
