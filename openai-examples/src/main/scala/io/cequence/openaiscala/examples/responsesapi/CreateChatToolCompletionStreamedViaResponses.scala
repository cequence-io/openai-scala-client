package io.cequence.openaiscala.examples.responsesapi

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.responsesapi.tools.{
  CodeInterpreterContainer,
  CodeInterpreterTool,
  WebSearchTool
}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ExampleBase}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

/**
 * The chat-completion-shaped way to stream through the Responses API: the same messages +
 * `CreateChatCompletionSettings` as `createChatToolCompletionStreamed` (and as the Anthropic /
 * Gemini `asOpenAI()` adapters), with Responses-native tools added via `setResponsesTools`.
 * Reasoning summaries are requested automatically when `reasoning_effort` is set, so
 * `ChatChunk.Thinking` chunks stream too.
 *
 * `service.responsesAsChatCompletion` gives the same as a reusable
 * `OpenAIChatCompletionStreamedService` (sync + typed streamed).
 *
 * Requires `openai-scala-client-stream` as a dependency.
 */
object CreateChatToolCompletionStreamedViaResponses
    extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  private val messages = Seq(
    SystemMessage("You are a terse assistant."),
    UserMessage(
      "First search the web for the most recent news about missions to Jupiter (one sentence). " +
        "Then compute the 30th Fibonacci number with python. Then call get_weather for Oslo."
    )
  )

  private val tools = Seq(
    FunctionTool(
      name = "get_weather",
      description = Some("Get the current weather in a city"),
      parameters = JsonSchema.Object(
        properties = Seq("location" -> JsonSchema.String(description = Some("City name"))),
        required = Seq("location")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletionStreamedViaResponses(
        messages = messages,
        tools = tools,
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_5_4,
          reasoning_effort = Some(ReasoningEffort.low)
        ).setResponsesTools(
          Seq(
            WebSearchTool(),
            CodeInterpreterTool(container = CodeInterpreterContainer.Auto())
          )
        )
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
