package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{
  BufferedImageHelper,
  ChatCompletionProvider,
  ExampleBase
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * Probes whether Anthropic can reference multiple images by their `filename` when each is
 * passed as `FileContent(fileData=..., filename=...)`.
 *
 * Anthropic's adapter routes images to `MediaBlock("image", ...)`. The Anthropic API's image
 * block has no `name`/`title` field, so the filename is currently dropped on the wire — this
 * example demonstrates what (if anything) the model can infer without it.
 *
 * Requires `ANTHROPIC_API_KEY` and `EXAMPLE_IMAGE_PATH_1` / `EXAMPLE_IMAGE_PATH_2`.
 */
object AnthropicCreateChatCompletionWithMultipleNamedImagesAsFileContent
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropic()

  private val img1 = new File(sys.env("EXAMPLE_IMAGE_PATH_1"))
  private val img2 = new File(sys.env("EXAMPLE_IMAGE_PATH_2"))

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(
      "You are an assistant looking at two attached image files. " +
        "Each file carries its filename as metadata. In your answer, refer to each image by " +
        "the filename that was attached to it."
    ),
    UserSeqMessage(
      Seq(
        TextContent(
          "I'm attaching two images. For EACH file, tell me one fact unique to that image. " +
            "Use the filenames from the file metadata in your answer (do not invent names)."
        ),
        FileContent(
          fileData = Some(fileBase64DataUrl(img1)),
          filename = Some(img1.getName)
        ),
        FileContent(
          fileData = Some(fileBase64DataUrl(img2)),
          filename = Some(img2.getName)
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_4_5_20250929,
          max_tokens = Some(2000)
        )
      )
      .map(response => println(response.contentHead))
}
