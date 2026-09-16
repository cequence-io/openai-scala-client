package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * ONE provider-neutral `MCPServerTool` (the keyless DeepWiki server), the same typed tool
 * stream on three providers: OpenAI (routed through the Responses API `mcp` tool), Anthropic
 * (the MCP connector) and Gemini (`mcpServers`). Every provider calls the server itself; the
 * calls arrive as server-side `ToolCallStart` / `ToolCall`, their results as `ToolResult`.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY`, `ANTHROPIC_API_KEY` and `GOOGLE_API_KEY`.
 */
object CreateChatToolCompletionStreamedWithMCPServerTool
    extends ExampleBase[CloseableService] {

  private val openAI = OpenAIServiceFactory.withStreaming()
  private val anthropic = ChatCompletionProvider.anthropic()
  // the native Gemini API adapter (ChatCompletionProvider.gemini is Gemini's OpenAI-compatible
  // endpoint, which has no mcpServers)
  private val gemini = GeminiServiceFactory.asOpenAI()

  override protected val service: CloseableService = new CloseableService {
    override def close(): Unit = {
      openAI.close()
      anthropic.close()
      gemini.close()
    }
  }

  private val deepwiki = MCPServerTool(
    name = "deepwiki",
    url = "https://mcp.deepwiki.com/mcp"
  )

  private val messages = Seq(
    SystemMessage("You are a terse assistant. Use the deepwiki tools to look things up."),
    UserMessage(
      "In one sentence: what is the 'given' keyword for in Scala 3? Check the scala/scala3 repository."
    )
  )

  private def run(
    label: String,
    service: OpenAIChatCompletionStreamedService,
    model: String
  ): Future[Unit] = {
    println("=" * 60)
    println(s"$label ($model)")
    println("=" * 60)

    service
      .createChatToolCompletionStreamed(
        messages = messages,
        tools = Seq(deepwiki),
        settings = CreateChatCompletionSettings(model = model, max_tokens = Some(2000))
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
      .map(_ => ())
  }

  override protected def run: Future[_] =
    for {
      _ <- run("OpenAI (Responses API)", openAI, ModelId.gpt_5_4)
      _ <- run("Anthropic (MCP connector)", anthropic, NonOpenAIModelId.claude_sonnet_5)
      _ <- run("Gemini (mcpServers)", gemini, NonOpenAIModelId.gemini_2_5_flash)
    } yield ()
}
