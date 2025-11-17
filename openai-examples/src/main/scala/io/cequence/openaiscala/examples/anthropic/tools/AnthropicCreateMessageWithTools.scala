package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.Tool
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.{JsonSchema, NonOpenAIModelId}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithTools extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val model = NonOpenAIModelId.claude_sonnet_4_5_20250929

  // Example 1: Custom tool (get_weather)
  private val weatherTool = Tool.custom(
    name = "get_weather",
    inputSchema = JsonSchema.ObjectAsMap(
      properties = Map(
        "location" -> JsonSchema.String(
          description = Some("The city and state, e.g. San Francisco, CA")
        ),
        "unit" -> JsonSchema.String(
          description = Some("Unit for the output - one of (celsius, fahrenheit)")
        )
      ),
      required = Seq("location")
    ),
    description = Some("Get the current weather in a given location")
  )

  private val messages1 = Seq(
    UserMessage("What's the weather like in San Francisco?")
  )

  private val settings1 = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 2048,
    tools = Seq(weatherTool)
  )

  // Example 2: Bash tool
  private val bashTool = Tool.bash()

  private val messages2 = Seq(
    UserMessage("List the files in the current directory using bash.")
  )

  private val settings2 = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 2048,
    tools = Seq(bashTool)
  )

  // Example 3: Web search tool
  private val webSearchTool = Tool.webSearch(
    maxUses = Some(3)
  )

  private val messages3 = Seq(
    UserMessage("Search the web for the latest news about AI.")
  )

  private val settings3 = AnthropicCreateMessageSettings(
    model = model,
    max_tokens = 2048,
    tools = Seq(webSearchTool)
  )

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

  override protected def run: Future[_] = {
    for {
      response1 <- {
        println("=" * 60)
        println("Example 1: Using custom tool (get_weather)")
        println("=" * 60)
        service.createMessage(messages1, settings1)
      }

      _ = printResponse(response1)

      response2 <- {
        println("=" * 60)
        println("Example 2: Using bash tool")
        println("=" * 60)
        service.createMessage(messages2, settings2)
      }

      _ = printResponse(response2)

      response3 <- {
        println("=" * 60)
        println("Example 3: Using web search tool")
        println("=" * 60)
        service.createMessage(messages3, settings3)
      }

    } yield {
      printResponse(response3)

      println("=" * 60)
      println("Available tool types:")
      println("  - CustomTool: User-defined tools with custom input schemas")
      println("  - BashTool: Execute bash commands")
      println("  - CodeExecutionTool: Execute code")
      println("  - ComputerUseTool: Control a computer")
      println("  - MemoryTool: Persist information across conversations")
      println("  - TextEditorTool: Edit text files")
      println("  - WebSearchTool: Search the web")
      println("  - WebFetchTool: Fetch web pages")
      println("=" * 60)
    }
  }
}
