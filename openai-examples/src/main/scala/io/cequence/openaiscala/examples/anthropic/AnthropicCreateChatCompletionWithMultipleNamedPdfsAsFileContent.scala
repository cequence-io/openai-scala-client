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
 * Verifies that for PDFs the `FileContent.filename` is propagated end-to-end to Anthropic via
 * the `document` MediaBlock's `title` field, so the model can refer to each PDF by name even
 * when the prompt does NOT mention the names.
 *
 * Requires `ANTHROPIC_API_KEY` and `EXAMPLE_PDF_PATH_1` / `EXAMPLE_PDF_PATH_2`.
 */
object AnthropicCreateChatCompletionWithMultipleNamedPdfsAsFileContent
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.anthropic()

  private val pdf1 = new File(sys.env("EXAMPLE_PDF_PATH_1"))
  private val pdf2 = new File(sys.env("EXAMPLE_PDF_PATH_2"))

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage(
      "You are an assistant looking at two attached PDF documents. " +
        "Each document carries its filename as metadata (document title). " +
        "Refer to each document by the filename that was attached to it."
    ),
    UserSeqMessage(
      Seq(
        TextContent(
          "I'm attaching two PDFs. For EACH file, tell me one fact unique to that document. " +
            "Use the filenames from the document metadata in your answer (do NOT invent names " +
            "and do NOT use any text I have already mentioned in this prompt)."
        ),
        FileContent(
          fileData = Some(fileBase64DataUrl(pdf1)),
          filename = Some(pdf1.getName)
        ),
        FileContent(
          fileData = Some(fileBase64DataUrl(pdf2)),
          filename = Some(pdf2.getName)
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
