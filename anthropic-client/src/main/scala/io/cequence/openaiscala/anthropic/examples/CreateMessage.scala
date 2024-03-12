package io.cequence.openaiscala.anthropic.examples

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.service.AnthropicCreateMessageSettings
import io.cequence.openaiscala.domain.ModelId

import scala.concurrent.Future

object CreateMessage extends Example {

  val messages: Seq[Message] = Seq(UserMessage("What is the weather like in Norway?"))

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = ModelId.claude_3_opus_20240229,
          max_tokens = 4096
        )
      )
      .map(printMessageContent)
}
