package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.WebSearchToolResultBlock
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.WebSearchToolResultContent
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.Tool
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithWebSearch extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val model = NonOpenAIModelId.claude_sonnet_4_5_20250929

  private val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant with access to web search."),
    UserMessage(
      "What are the latest developments in quantum computing in 2025? Please search for recent information."
    )
  )

  override protected def run: Future[_] =
    for {
      response <- service.createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = model,
          max_tokens = 4096,
          tools = Seq(Tool.webSearch())
        )
      )

    } yield {
      // Extract web search results
      val webSearchResults = response.blockContents.collect {
        case WebSearchToolResultBlock(content, toolUseId) =>
          (content, toolUseId)
      }

      if (webSearchResults.nonEmpty) {
        println()
        println("=" * 60)
        println("Web Search Results:")
        println("=" * 60)
        println()

        webSearchResults.foreach { case (content, toolUseId) =>
          println(s"Tool Use ID: $toolUseId")
          println()

          content match {
            case WebSearchToolResultContent.Success(results) =>
              println(s"Found ${results.length} results:")
              println()
              results.zipWithIndex.foreach { case (result, index) =>
                println(s"Result ${index + 1}:")
                println(s"  Title: ${result.title}")
                println(s"  URL: ${result.url}")
                result.pageAge.foreach(age => println(s"  Page Age: $age"))
                println(s"  Content Preview: ${result.encryptedContent.take(200)}...")
                println()
              }

            case WebSearchToolResultContent.Error(errorCode) =>
              println(s"Error: $errorCode")
              println()
          }

          println("-" * 60)
        }
      }

      // Display all content blocks
      println()
      println("=" * 60)
      println("Complete Response:")
      println("=" * 60)
      response.blockContents.zipWithIndex.foreach { case (blockContent, index) =>
        println(s"Block ${index + 1}:")
        println(blockContent)
        println("=" * 60)
      }

      // Display text summary
      if (response.texts.nonEmpty) {
        println()
        println("=" * 60)
        println("Text Summary:")
        println("=" * 60)
        println(response.text)
        println("=" * 60)
      }
    }
}
