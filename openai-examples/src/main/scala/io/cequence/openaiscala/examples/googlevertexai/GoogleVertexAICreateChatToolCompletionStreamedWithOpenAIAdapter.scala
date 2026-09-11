package io.cequence.openaiscala.examples.googlevertexai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.vertexai.domain.Tool
import io.cequence.openaiscala.vertexai.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.Future

/**
 * Typed streaming through the Vertex AI adapter: thoughts (requested automatically on the
 * typed path; `setVertexAIIncludeThoughts(false)` turns them off), a client-side function
 * tool, and Vertex's server-side code execution whose `executableCode` / `codeExecutionResult`
 * parts arrive as `ChatChunk.ToolCall(serverSide = true)` + `ChatChunk.ToolResult` (plus the
 * semantic `CodeExecution` / `CodeExecutionResult` chunks). Google Search grounding
 * (`Tool.GoogleSearch`) surfaces as `WebSearch` / `WebSearchResult` / `Citation` chunks.
 *
 * Requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and
 * `VERTEXAI_PROJECT_ID` environment variables to be set.
 */
object GoogleVertexAICreateChatToolCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = VertexAIServiceFactory.asOpenAI()

  private val model = NonOpenAIModelId.gemini_2_5_flash

  private val weatherTool = FunctionTool(
    name = "get_weather",
    description = Some("Get the current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("location" -> JsonSchema.String(description = Some("City name"))),
      required = Seq("location")
    )
  )

  override protected def run: Future[_] =
    for {
      _ <- service
        .createChatToolCompletionStreamed(
          messages = Seq(
            SystemMessage("You are a terse assistant."),
            UserMessage("What is the weather in Oslo right now? Use the get_weather tool.")
          ),
          tools = Seq(weatherTool),
          settings = CreateChatCompletionSettings(
            model = model,
            reasoning_effort = Some(ReasoningEffort.low)
          )
        )
        .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))

      _ = println("\n--- server-side code execution ---")

      _ <- service
        .createChatToolCompletionStreamed(
          messages = Seq(
            UserMessage(
              "Compute the 30th Fibonacci number by writing and running Python code, then state the result."
            )
          ),
          settings = CreateChatCompletionSettings(
            model = model,
            reasoning_effort = Some(ReasoningEffort.low)
          ).setVertexAITools(Seq(Tool.CodeExecution))
        )
        .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
    } yield ()
}
