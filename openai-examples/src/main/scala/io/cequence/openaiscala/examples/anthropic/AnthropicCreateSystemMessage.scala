package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateSystemMessage extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  val systemMessages: Seq[Message] = Seq(
    SystemMessage("Talk in pirate speech")
  )
  val messages: Seq[Message] = Seq(
    UserMessage("Who is the most famous football player in the World?")
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        systemMessages ++ messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_3_haiku_20240307,
          max_tokens = 4096
        )
      )
      .map(printMessageContent)

  private def printMessageContent(response: CreateMessageResponse) =
    println(response.text)
}
