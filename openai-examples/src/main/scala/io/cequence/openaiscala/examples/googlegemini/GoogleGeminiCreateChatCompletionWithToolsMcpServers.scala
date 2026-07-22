package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.domain.ChatRole.User
import io.cequence.openaiscala.gemini.domain.{
  Content,
  McpServer,
  Part,
  StreamableHttpTransport,
  Tool
}
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}

import scala.concurrent.Future

/**
 * Example showing remote MCP servers with Gemini (`Tool.McpServers`).
 *
 * The Gemini backend connects to the given MCP server(s) DIRECTLY and invokes their tools
 * server-side - no client-side tool loop is needed; the final answer arrives in one
 * `generateContent` response. Only streamable-HTTP transport is supported; auth (if any) rides
 * as custom HTTP headers on the transport (e.g. `x-api-key`).
 *
 * This example uses the public DeepWiki MCP server (no auth) and asks a question that requires
 * calling its tools. Requires `openai-scala-google-gemini-client` as a dependency and the
 * `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithToolsMcpServers extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  private val tools = Seq(
    Tool.McpServers(
      Seq(
        McpServer(
          name = "deepwiki",
          streamableHttpTransport = StreamableHttpTransport(
            url = "https://mcp.deepwiki.com/mcp",
            timeout = Some("120s")
          )
        )
      )
    )
  )

  private val contents: Seq[Content] = Seq(
    Content.textPart(
      "Using the deepwiki tools, list the main wiki sections available for the " +
        "'cequence-io/openai-scala-client' GitHub repository - just the section titles.",
      User
    )
  )

  override protected def run: Future[_] =
    service
      .generateContent(
        contents,
        settings = GenerateContentSettings(
          model = NonOpenAIModelId.gemini_3_flash_preview,
          tools = Some(tools),
          generationConfig = Some(
            GenerationConfig(
              maxOutputTokens = Some(2000)
            )
          )
        )
      )
      .map { response =>
        println("Response (via the DeepWiki MCP server):")
        println(response.contentHeadText)

        // Report any tool-call / tool-result blocks in the response. For `Tool.McpServers`
        // Gemini executes the MCP tools entirely SERVER-side and (verified July 2026 on both
        // generateContent and streamGenerateContent) does NOT return the blocks - expect
        // "(none)" here, with `usageMetadata.toolUsePromptTokenCount` below as the observable
        // evidence of the calls. The collection is kept so the blocks surface immediately
        // should Google start echoing them.
        val parts = response.candidates.flatMap(_.content.parts)

        val toolCalls = parts.collect { case fc: Part.FunctionCall => fc }
        val toolResults = parts.collect { case fr: Part.FunctionResponse => fr }
        // block types this client does not model yet parse as Part.Unknown (never dropped)
        val unknownBlocks = parts.collect { case u: Part.Unknown => u }

        println("\nTool call/result blocks returned:")
        if (toolCalls.isEmpty && toolResults.isEmpty && unknownBlocks.isEmpty)
          println("  (none - Gemini runs MCP tools server-side and does not echo the blocks)")
        else {
          toolCalls.foreach(fc => println(s"  - call:    ${fc.name}(${fc.args})"))
          toolResults.foreach(fr => println(s"  - result:  ${fr.name} -> ${fr.response}"))
          unknownBlocks.foreach(u => println(s"  - unknown: ${u.data}"))
        }

        val usage = response.usageMetadata
        println(
          s"\nUsage: prompt=${usage.promptTokenCount}, " +
            s"toolUsePrompt=${usage.toolUsePromptTokenCount.getOrElse(0)} " +
            "(> 0 proves the backend called the MCP server), " +
            s"thoughts=${usage.thoughtsTokenCount.getOrElse(0)}, " +
            s"total=${usage.totalTokenCount}"
        )
      }
}
