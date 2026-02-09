package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

/**
 * Demonstrates how to list available tools from MCP servers through the Anthropic API.
 *
 * Unlike OpenAI's Responses API which has a dedicated MCPListTools response type, Anthropic's
 * API doesn't have a dedicated endpoint for listing MCP tools. However, you can ask Claude to
 * introspect and list the available tools from an MCP server.
 *
 * Claude uses the MCP protocol internally (including the tools/list method) to discover
 * available tools, and can describe them in its response.
 *
 * Requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment
 * variable to be set.
 */
object AnthropicListMCPServerTools extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val model = NonOpenAIModelId.claude_haiku_4_5_20251001

  // DeepSense CMS Coverage MCP Server
  private val cmsCoverageMcpServer = MCPServerURLDefinition(
    name = "cms_coverage",
    url = "https://mcp.deepsense.ai/cms_coverage/mcp"
  )

  // Ask Claude to list available tools from the MCP server
  private val listToolsMessage = Seq(
    UserMessage(
      """List all available tools from the connected MCP server.
        |For each tool, provide:
        |1. Tool name
        |2. Description (if available)
        |3. Input parameters/schema (if available)
        |
        |Format the output as a structured list.""".stripMargin
    )
  )

  private val settings = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 4096,
    mcp_servers = Seq(cmsCoverageMcpServer)
  )

  override protected def run: Future[_] = {
    println("=" * 60)
    println("Listing MCP Server Tools: DeepSense CMS Coverage")
    println("=" * 60)
    println()

    service
      .createMessage(listToolsMessage, settings)
      .map { response =>
        printResponse(response)
      }
      .recover { case e: Throwable =>
        println("Error received:")
        e.printStackTrace()
      }
  }

  private def printResponse(response: CreateMessageResponse): Unit = {
    println(s"Model: ${response.model}")
    println(s"Stop Reason: ${response.stop_reason.getOrElse("N/A")}")
    println()
    println("Available Tools:")
    println("-" * 40)

    response.blockContents.foreach { blockContent =>
      println(blockContent)
    }
  }
}
