package io.cequence.openaiscala.examples.googlegemini

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
import io.cequence.openaiscala.gemini.domain.Tool
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Typed streaming through the Gemini adapter: thought summaries (requested automatically on
 * the typed path when `reasoning_effort` is set), a client-side function tool, and Gemini's
 * server-side code execution whose `executableCode` / `codeExecutionResult` parts arrive as
 * `ChatChunk.ToolCall(serverSide = true)` + `ChatChunk.ToolResult`.
 *
 * Requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY`.
 */
object GoogleGeminiCreateChatToolCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = GeminiServiceFactory.asOpenAI()

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
            model = NonOpenAIModelId.gemini_3_8_flash,
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
            model = NonOpenAIModelId.gemini_3_8_flash,
            reasoning_effort = Some(ReasoningEffort.low)
          ).setGeminiTools(Seq(Tool.CodeExecution))
        )
        .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
    } yield ()
}
