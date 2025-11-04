package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{MediaBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.UserMessageContent
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.{BufferedImageHelper, ExampleBase}

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency
object AnthropicCreateMessageWithImage
    extends ExampleBase[AnthropicService]
    with BufferedImageHelper {

  private lazy val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val imageSource = imageBase64Source(new java.io.File(localImagePath))

  override protected val service: AnthropicService = AnthropicServiceFactory()

  private val messages: Seq[Message] = Seq(
    Message.SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessageContent(
      Seq(
        ContentBlockBase(TextBlock("Summarize the document.")),
        MediaBlock.jpeg(data = imageSource)
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_3_7_sonnet_20250219,
          max_tokens = 4096
        )
      )
      .map(printMessageContent)

  private def printMessageContent(response: CreateMessageResponse) =
    println(response.text)
}
