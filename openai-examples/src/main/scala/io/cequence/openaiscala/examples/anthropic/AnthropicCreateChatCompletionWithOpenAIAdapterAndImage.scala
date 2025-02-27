package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.examples.{
  BufferedImageHelper,
  ChatCompletionProvider,
  ExampleBase
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionWithOpenAIAdapterAndImage
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localImagePath = sys.env("EXAMPLE_IMAGE_PATH")
  private val imageSource = imageBase64Source(new java.io.File(localImagePath))

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropic()

  private val messages = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserSeqMessage(
      Seq(
        TextContent("Summarize the document."),
        ImageURLContent(s"data:image/jpeg;base64,${imageSource}")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_3_5_sonnet_20241022)
      )
      .map { content =>
        println(content.choices.headOption.map(_.message.content).getOrElse("N/A"))
      }
}
