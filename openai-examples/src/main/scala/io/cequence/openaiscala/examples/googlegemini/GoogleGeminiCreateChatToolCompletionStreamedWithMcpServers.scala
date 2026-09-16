package io.cequence.openaiscala.examples.googlegemini

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ExampleBase}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.{McpServer, StreamableHttpTransport, Tool}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Gemini's native MCP support (`Tool.McpServers`) on the typed stream, through the OpenAI
 * adapter: two remote servers in one request - Exa (authenticated with an `x-api-key` header;
 * an `Authorization: Bearer ...` header works the same way) and the keyless DeepWiki. Gemini
 * calls their tools itself; on Gemini 2.5 each call streams back as a server-side
 * `ToolCallStart` / `ToolCall` and its result as a `ToolResult` (named `<server>_<tool>`),
 * followed by the answer and ONE `Finish`. Gemini 3 runs the tools too but echoes no call /
 * result parts - only `usage` (the tool-use prompt tokens) shows they ran.
 *
 * Gemini's MCP executor fails transiently (an HTTP 500 / 503, or a stream that ends right
 * after the call): the adapter then reports the call as a `ToolResult(isError = true)` instead
 * of an empty answer - retry when you see it.
 *
 * Requires `openai-scala-google-gemini-client` as a dependency, `GOOGLE_API_KEY`, and
 * `MCP_EXA_SEARCH_TOKEN` (an Exa API key).
 */
object GoogleGeminiCreateChatToolCompletionStreamedWithMcpServers
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = GeminiServiceFactory.asOpenAI()

  private val exaApiKey = sys.env.getOrElse(
    "MCP_EXA_SEARCH_TOKEN",
    throw new IllegalStateException("MCP_EXA_SEARCH_TOKEN environment variable expected")
  )

  private val mcpServers = Tool.McpServers(
    Seq(
      McpServer(
        name = "exa",
        streamableHttpTransport = StreamableHttpTransport(
          url = "https://mcp.exa.ai/mcp",
          headers = Some(Map("x-api-key" -> exaApiKey)),
          timeout = Some("120s")
        )
      ),
      McpServer(
        name = "deepwiki",
        streamableHttpTransport = StreamableHttpTransport(
          url = "https://mcp.deepwiki.com/mcp",
          timeout = Some("120s")
        )
      )
    )
  )

  private val messages = Seq(
    UserMessage(
      "First use the exa web search tool to find the latest news about missions to Jupiter (one sentence), " +
        "then use the deepwiki tools to say in one sentence what the scala/scala3 repository is about."
    )
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletionStreamed(
        messages = messages,
        tools = Nil,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.gemini_2_5_flash,
          max_tokens = Some(2000)
        ).setGeminiTools(Seq(mcpServers))
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
