package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{BufferedImageHelper, Example}

import java.io.File
import java.nio.file.Files
import java.util.Base64
import scala.concurrent.Future

/**
 * Working pattern for passing multiple named JPEGs to OpenAI: interleave a `TextContent` label
 * before each `ImageURLContent` so the model can refer to each image by name. This works on
 * the standard chat completions endpoint — no Responses API adapter required.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `EXAMPLE_IMAGE_PATH_1` / `EXAMPLE_IMAGE_PATH_2`.
 */
object CreateChatCompletionWithMultipleLabeledImages extends Example with BufferedImageHelper {

  private val img1 = new File(sys.env("EXAMPLE_IMAGE_PATH_1"))
  private val img2 = new File(sys.env("EXAMPLE_IMAGE_PATH_2"))

  private def jpegDataUrl(file: File): String = {
    val data = Base64.getEncoder.encodeToString(Files.readAllBytes(file.toPath))
    s"data:image/jpeg;base64,$data"
  }

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(
      "You are an assistant looking at several attached invoice pages. " +
        "Each image is preceded by a text label like '[file: NAME]'. " +
        "Use these labels to refer to each image in your answer."
    ),
    UserSeqMessage(
      Seq(
        TextContent(
          "Attached are two pages of an invoice. For EACH file, tell me one fact unique " +
            s"to that page. Use the exact filenames '${img1.getName}' and '${img2.getName}' " +
            "from the labels in your answer."
        ),
        TextContent(s"[file: ${img1.getName}]"),
        ImageURLContent(jpegDataUrl(img1)),
        TextContent(s"[file: ${img2.getName}]"),
        ImageURLContent(jpegDataUrl(img2))
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model = ModelId.gpt_4_1)
      )
      .map(printMessageContent)
}
