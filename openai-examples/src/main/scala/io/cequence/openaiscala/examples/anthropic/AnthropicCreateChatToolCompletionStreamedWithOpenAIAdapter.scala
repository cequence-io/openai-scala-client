package io.cequence.openaiscala.examples.anthropic

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.tools.Tool
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Typed streaming through the Anthropic adapter: summarized thinking (requested automatically
 * on the typed path), a client-side function tool, and the Anthropic-native web search tool
 * whose results arrive as `ChatChunk.ToolResult`s in the same stream.
 *
 * Requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateChatToolCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService =
    ChatCompletionProvider.anthropic()

  private val messages = Seq(
    SystemMessage("You are a terse assistant."),
    UserMessage(
      "First search the web for the most recent news about missions to Jupiter and summarize it in one sentence. " +
        "Then call get_weather for Oslo."
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
      .createChatToolCompletionStreamed(
        messages = messages,
        tools = tools,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_5,
          max_tokens = Some(4000),
          reasoning_effort = Some(ReasoningEffort.medium)
        ).setAnthropicTools(Seq(Tool.webSearch()))
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
