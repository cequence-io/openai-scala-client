package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.{
  MCPServerURLDefinition,
  MCPToolConfiguration
}
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
    url = "https://mcp.deepwiki.com/sse",
    toolConfiguration = Some(
      MCPToolConfiguration(
        allowedTools = Nil,
        enabled = Some(true)
      )
    )
  )

  private val messages1 = Seq(
    UserMessage(
      "What is the purpose of the 'given' keyword in Scala 3? Search in scala/scala repository."
    )
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

  // Example 3: DeepSense CMS Coverage MCP Server
  private val cmsCoverageMcpServer = MCPServerURLDefinition(
    name = "cms_coverage",
    url = "https://mcp.deepsense.ai/cms_coverage/mcp"
  )

  private val messages3 = Seq(
    UserMessage(
      "What are the recent National Coverage Determinations (NCDs) published in the last 30 days? Include any updates to oncology-related coverage policies."
    )
  )

  private val settings3 = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 2048,
    mcp_servers = Seq(cmsCoverageMcpServer)
  )

  private def runExample(
    name: String,
    messages: Seq[UserMessage],
    settings: AnthropicCreateMessageSettings
  ): Future[Option[CreateMessageResponse]] = {
    println("=" * 60)
    println(s"Example: $name")
    println("=" * 60)

    service.createMessage(messages, settings).map(Some(_)).recover { case e: Throwable =>
      println("Error received:")
      e.printStackTrace()
      None
    }
  }

  override protected def run: Future[_] =
    runExample("Using DeepWiki MCP Server", messages1, settings1).flatMap { response1 =>
      printResponse(response1)
      runExample("Using Semgrep MCP Server", messages2, settings2)
    }.flatMap { response2 =>
      printResponse(response2)
      runExample("Using DeepSense CMS Coverage MCP Server", messages3, settings3)
    }.map { response3 =>
      printResponse(response3)

      println("=" * 60)
      println("Available MCP servers:")
      println("  - DeepWiki: Access to Wikipedia and other knowledge sources")
      println("  - Semgrep: Code analysis and security vulnerability detection")
      println("  - DeepSense CMS Coverage: Medicare coverage policies (NCDs, LCDs)")
      println("=" * 60)
    }

  private def printResponse(response: Option[CreateMessageResponse]): Unit = {
    response.foreach { r =>
      println(s"Model: ${r.model}")
      println(s"Role: ${r.role}")
      println(s"Stop Reason: ${r.stop_reason.getOrElse("N/A")}")
      println()

      r.blockContents.foreach { blockContent =>
        println(s"Content Block:")
        println(s"  ${blockContent}")
        println()
      }
    }
  }
}
