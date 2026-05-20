package io.cequence.openaiscala.examples.googlevertexai

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
 * Probes whether `FileContent.filename` is propagated to Vertex AI when multiple files are
 * attached.
 *
 * '''Result: NO.''' Vertex's `Blob` (inline data) carries only `mime_type` and `data` on the
 * wire (no filename field, verified via the protobuf descriptor), so the filename never
 * reaches the model. Empirically the model invents plausible-sounding names from the document
 * content (e.g. "1.pdf", "Faktura_FV10692.jpg"). This is consistent for both PDFs and images.
 *
 * If you need filename-anchored references, either include the filename in a `TextContent`
 * label before each file, or upload via GCS and reference by `file_uri`.
 *
 * Run with PDFs by setting `EXAMPLE_FILE_PATH_1` / `EXAMPLE_FILE_PATH_2` to two PDFs; run with
 * images by pointing them at two JPEGs.
 *
 * Requires `VERTEXAI_PROJECT_ID`, `VERTEXAI_LOCATION`.
 */
object GoogleVertexAICreateChatCompletionWithMultipleNamedFilesAsFileContent
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.vertexAI

  private val file1 = new File(sys.env("EXAMPLE_FILE_PATH_1"))
  private val file2 = new File(sys.env("EXAMPLE_FILE_PATH_2"))

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(
      "You are an assistant looking at two attached files. " +
        "Each file carries its filename as metadata. Refer to each file by " +
        "the filename that was attached to it."
    ),
    UserSeqMessage(
      Seq(
        TextContent(
          "I'm attaching two files. For EACH file, tell me one fact unique to that document. " +
            "Use the filenames from the file metadata in your answer. " +
            "If you don't see filenames, say 'no filename available' instead of inventing one."
        ),
        FileContent(
          fileData = Some(fileBase64DataUrl(file1)),
          filename = Some(file1.getName)
        ),
        FileContent(
          fileData = Some(fileBase64DataUrl(file2)),
          filename = Some(file2.getName)
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.gemini_2_5_pro,
          max_tokens = Some(2000)
        )
      )
      .map(response => println(response.contentHead))
}
