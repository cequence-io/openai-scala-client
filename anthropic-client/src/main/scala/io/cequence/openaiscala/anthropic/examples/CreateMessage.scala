package io.cequence.openaiscala.anthropic.examples

import io.cequence.openaiscala.anthropic.domain.Content.TextContent
import io.cequence.openaiscala.anthropic.domain.{BaseMessage, ChatRole}
import io.cequence.openaiscala.anthropic.service.AnthropicCreateChatCompletionSettings

import scala.concurrent.Future

object CreateMessage extends Example {

  val messages = Seq(
    BaseMessage(ChatRole.User, Seq(TextContent("What is the weather like in Norway?")))
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateChatCompletionSettings(
          // TODO: create constants
          model = "claude-3-opus-20240229",
          max_tokens = 4096,
        ))
      .map(printMessageContent)
}
