package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlockBase, SingleString}
import io.cequence.openaiscala.anthropic.domain.{Content, Message}
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessage extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory(withCache = true)

  val systemMessage: Content = SingleString("You are a helpful assistant.")
  val messages: Seq[Message] = Seq(UserMessage("What is the weather like in Norway?"))

  override protected def run: Future[_] =
    service
      .createMessage(
        Some(systemMessage),
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_3_haiku_20240307,
          max_tokens = 4096
        )
      )
      .map(printMessageContent)

  private def printMessageContent(response: CreateMessageResponse) = {
    val text =
      response.content.blocks.collect { case ContentBlockBase(TextBlock(text), _) => text }
        .mkString(" ")
    println(text)
  }
}
