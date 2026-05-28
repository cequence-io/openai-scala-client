package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{BufferedImageHelper, Example}
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService

import java.io.File
import java.nio.file.Files
import scala.concurrent.Future

/**
 * Sends two JPEGs through OpenAI's Responses API and has the model refer to each one by
 * filename. OpenAI's `input_file` rejects image MIME types (only `application/pdf` is accepted
 * there), so we can't carry the name on the file block itself. Instead we use
 * [[VLMContent.of]], which emits a `[file: NAME]` text label followed by an `ImageURLContent`
 * (data URL) for each image. The label gives the model a stable handle that the system prompt
 * instructs it to echo back verbatim.
 *
 * Same convention works uniformly across providers — see the multi-named-files examples for
 * Anthropic / Gemini / Vertex.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `EXAMPLE_IMAGE_PATH_1` / `EXAMPLE_IMAGE_PATH_2`.
 */
object CreateChatCompletionWithMultipleNamedImagesViaResponsesAPIImproved
    extends Example
    with BufferedImageHelper {

  private val chatService = OpenAIResponsesChatCompletionService(service)

  private val img1 = new File(sys.env("EXAMPLE_IMAGE_PATH_1"))
  private val img2 = new File(sys.env("EXAMPLE_IMAGE_PATH_2"))

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(
      "You are an assistant looking at two attached invoice/document pages. " +
        "In your answer, ALWAYS reference each page by its filename (the value of the `filename` field)."
    ),
    UserMessage(
      "I'm attaching two pages of the same invoice. " +
        "For EACH file, tell me one fact unique to that page. " +
        s"Use the exact filenames '${img1.getName}' and '${img2.getName}' in your answer."
    ),
    UserSeqMessage(
      VLMContent.of(
        Files.readAllBytes(img1.toPath),
        img1.getName
      ) ++ VLMContent.of(
        Files.readAllBytes(img2.toPath),
        img2.getName
      )
    )
  )

  override protected def run: Future[_] =
    chatService
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model = ModelId.gpt_4_1)
      )
      .map { response =>
        println(response.contentHead)
        println(s"\n[usage] ${response.usage}")
      }
}
