package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithMCPServer extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val model = NonOpenAIModelId.claude_sonnet_4_5_20250929

  // Example 1: DeepWiki MCP Server
  private val deepwikiMcpServer = MCPServerURLDefinition(
    name = "deepwiki",
    url = "https://mcp.deepwiki.com/sse"
  )

  private val messages1 = Seq(
    UserMessage("Search for information about Scala programming language in scala/scala.")
  )

  private val settings1 = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 2048,
    mcp_servers = Seq(deepwikiMcpServer)
  )

  // Example 2: Semgrep MCP Server
  private val semgrepMcpServer = MCPServerURLDefinition(
    name = "semgrep",
    url = "https://mcp.semgrep.ai/mcp"
  )

  private val messages2 = Seq(
    UserMessage(
      "Analyze this code for security vulnerabilities: def unsafe(input: String) = s\"SELECT * FROM users WHERE name = '$input'\""
    )
  )

  private val settings2 = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 2048,
    mcp_servers = Seq(semgrepMcpServer)
  )

  override protected def run: Future[_] =
    for {
      response1 <- {
        println("=" * 60)
        println("Example 1: Using DeepWiki MCP Server")
        println("=" * 60)
        service.createMessage(messages1, settings1)
      }

      _ = printResponse(response1)

      response2 <- {
        println("=" * 60)
        println("Example 2: Using Semgrep MCP Server")
        println("=" * 60)
        service.createMessage(messages2, settings2)
      }

    } yield {
      printResponse(response2)

      println("=" * 60)
      println("Available MCP servers:")
      println("  - DeepWiki: Access to Wikipedia and other knowledge sources")
      println("  - Semgrep: Code analysis and security vulnerability detection")
      println("=" * 60)
    }

  private def printResponse(response: CreateMessageResponse): Unit = {
    println(s"Model: ${response.model}")
    println(s"Role: ${response.role}")
    println(s"Stop Reason: ${response.stop_reason.getOrElse("N/A")}")
    println()

    response.blockContents.foreach { blockContent =>
      println(s"Content Block:")
      println(s"  ${blockContent}")
      println()
    }
  }
}
