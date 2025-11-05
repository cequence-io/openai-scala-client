package io.cequence.openaiscala.examples.anthropic.tools

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.WebFetchToolResultBlock
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.WebFetchToolResultContent
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.Tool
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithWebFetch extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val model = NonOpenAIModelId.claude_sonnet_4_5_20250929

  private val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant with access to web fetch."),
    UserMessage(
      "Please fetch the content from https://docs.anthropic.com and summarize the main topics covered."
    )
  )

  override protected def run: Future[_] =
    for {
      response <- service.createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = model,
          max_tokens = 4096,
          tools = Seq(Tool.webFetch())
        )
      )

    } yield {
      // Extract web fetch results
      val webFetchResults = response.blockContents.collect {
        case WebFetchToolResultBlock(content, toolUseId) =>
          (content, toolUseId)
      }

      if (webFetchResults.nonEmpty) {
        println()
        println("=" * 60)
        println("Web Fetch Results:")
        println("=" * 60)
        println()

        webFetchResults.foreach { case (content, toolUseId) =>
          println(s"Tool Use ID: $toolUseId")
          println()

          content match {
            case WebFetchToolResultContent.Success(document, url, retrievedAt) =>
              println(s"Successfully fetched from: $url")
              println(s"Retrieved at: $retrievedAt")
              println()
              println(s"Document Title: ${document.title}")
              println(s"Citations Enabled: ${document.citations.enabled}")
              println()
              println(s"Source:")
              println(s"  Type: ${document.source.`type`}")
              println(s"  Media Type: ${document.source.mediaType}")
              println(
                s"  Data (first 200 chars): ${document.source.data
                    .take(200)}${if (document.source.data.length > 200) "..." else ""}"
              )
              println()

              if (document.source.`type` == "base64") {
                println(
                  "Note: The source data is base64-encoded and can be decoded to access the original content."
                )
                println()
              }

            case WebFetchToolResultContent.Error(errorCode) =>
              println(s"Error fetching content: $errorCode")
              println()
              errorCode match {
                case WebFetchToolResultContent.WebFetchErrorCode.invalid_tool_input =>
                  println("The provided input was invalid.")
                case WebFetchToolResultContent.WebFetchErrorCode.url_too_long =>
                  println("The URL is too long.")
                case WebFetchToolResultContent.WebFetchErrorCode.url_not_allowed =>
                  println("The URL is not in the allowed domains list.")
                case WebFetchToolResultContent.WebFetchErrorCode.url_not_accessible =>
                  println("The URL could not be accessed.")
                case WebFetchToolResultContent.WebFetchErrorCode.unsupported_content_type =>
                  println("The content type is not supported.")
                case WebFetchToolResultContent.WebFetchErrorCode.too_many_requests =>
                  println("Too many requests were made.")
                case WebFetchToolResultContent.WebFetchErrorCode.max_uses_exceeded =>
                  println("Maximum number of uses has been exceeded.")
                case WebFetchToolResultContent.WebFetchErrorCode.unavailable =>
                  println("The web fetch service is currently unavailable.")
              }
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

      // Display citations if present
      if (response.citations.exists(_.nonEmpty)) {
        println()
        println("=" * 60)
        println("Citations:")
        println("=" * 60)
        response.citations.zipWithIndex.foreach { case (citationSeq, textIndex) =>
          if (citationSeq.nonEmpty) {
            println(s"For text block ${textIndex + 1}:")
            citationSeq.zipWithIndex.foreach { case (citation, citIndex) =>
              println(s"  Citation ${citIndex + 1}:")
              println(s"    Cited Text: ${citation.citedText}")
              println(s"    Type: ${citation.`type`}")
              println()
            }
          }
        }
        println("=" * 60)
      }
    }
}
