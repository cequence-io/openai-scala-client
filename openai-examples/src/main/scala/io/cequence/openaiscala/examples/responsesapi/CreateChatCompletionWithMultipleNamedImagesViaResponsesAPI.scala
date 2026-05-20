package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{BufferedImageHelper, Example}
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * Probes whether OpenAI's Responses API accepts JPEGs as named `input_file` entries (via the
 * `FileContent → InputMessageContent.File` adapter path), so the model could reference each
 * image by its `filename`.
 *
 * '''Result: NO — this example fails with HTTP 400.''' OpenAI's `input_file` rejects image
 * MIME types entirely (same restriction as the chat-completions `file` block, which only
 * accepts `application/pdf`):
 *
 * {{{
 *   'input[0].content[1].file_data' ... unsupported MIME type 'image/jpeg'.
 *   Please see https://platform.openai.com/docs/assistants/tools/file-search#supported-files
 * }}}
 *
 * '''Supported workarounds for naming images on OpenAI:'''
 *   1. Pre-upload each JPEG via the Files API to get a `file_id` (OpenAI stores the original
 *      filename), then reference the image by id. The Responses API's `input_image` accepts a
 *      `file_id`; for chat completions you'd need the assistants/responses path. 2. Include
 *      the filename as a text prefix before each `ImageURLContent` so the model can tie its
 *      caption to a name, e.g. `TextContent("[file: invoice-1.jpg]")` then
 *      `ImageURLContent(dataUrl1)`.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `EXAMPLE_IMAGE_PATH_1` / `EXAMPLE_IMAGE_PATH_2`.
 */
object CreateChatCompletionWithMultipleNamedImagesViaResponsesAPI
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
    UserSeqMessage(
      Seq(
        TextContent(
          "I'm attaching two pages of the same invoice. " +
            "For EACH file, tell me one fact unique to that page. " +
            s"Use the exact filenames '${img1.getName}' and '${img2.getName}' in your answer."
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
