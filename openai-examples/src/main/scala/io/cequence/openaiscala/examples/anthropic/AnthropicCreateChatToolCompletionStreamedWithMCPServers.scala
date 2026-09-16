package io.cequence.openaiscala.examples.anthropic

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Anthropic's MCP connector on the typed stream: the remote MCP server goes in via
 * `setAnthropicMcpServers`, Claude calls its tools itself, and every call / result arrives as
 * a server-side `ToolCallStart` / `ToolCall` / `ToolResult` in the same stream as the text.
 * When a tool run outlives the turn budget (`pause_turn`) the adapter resumes the turn on the
 * same `Source` - you only ever see one `Finish` (cap it with `setAnthropicMaxContinuations`).
 *
 * Uses DeepWiki's public streamable-HTTP MCP server (no key needed). Requires
 * `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY`.
 */
object AnthropicCreateChatToolCompletionStreamedWithMCPServers
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService =
    ChatCompletionProvider.anthropic()

  // no tool configuration = all of the server's tools; Tool.mcpServer(...) narrows them down
  private val deepwiki = MCPServerURLDefinition("deepwiki", "https://mcp.deepwiki.com/mcp")

  private val messages = Seq(
    SystemMessage("You are a terse assistant. Use the deepwiki tools to look things up."),
    UserMessage(
      "What is the purpose of the 'given' keyword in Scala 3? Check the scala/scala3 repository and answer in two sentences."
    )
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletionStreamed(
        messages = messages,
        tools = Nil,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_5,
          max_tokens = Some(4000),
          reasoning_effort = Some(ReasoningEffort.low)
        ).setAnthropicMcpServers(Seq(deepwiki)).setAnthropicMaxContinuations(4)
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
