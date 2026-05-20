package io.cequence.openaiscala.examples.cerebras

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
 * Cerebras (OpenAI-compatible) probe with a PDF passed as `FileContent`.
 *
 * '''NOTE — this example is expected to fail with HTTP 400.''' Cerebras's API gateway only
 * accepts content types `text`, `image_url`, and `image`; it does not accept OpenAI's `file`
 * content block. The rejection happens at the gateway (schema validation), so it is
 * model-independent:
 *
 * {{{
 *   Input tag 'file' found using 'type' does not match any of the expected tags:
 *   'text', 'image_url', 'image'
 * }}}
 *
 * The example is kept to document that `FileContent` serializes correctly on the wire
 * (Cerebras parses it and reports a precise schema error) and that Cerebras does not currently
 * support PDF/document inputs in any form.
 *
 * Requires `CEREBRAS_API_KEY` and `EXAMPLE_PDF_PATH`.
 */
object CerebrasCreateChatCompletionWithFileContentAndPdf
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localPdfPath = sys.env("EXAMPLE_PDF_PATH")
  private val pdfBase64 = pdfBase64Source(new File(localPdfPath))

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.cerebras

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant that summarizes documents."),
    UserSeqMessage(
      Seq(
        TextContent("Summarize this document in 3 bullet points."),
        FileContent(
          fileData = Some(s"data:application/pdf;base64,$pdfBase64"),
          filename = Some(new File(localPdfPath).getName)
        )
      )
    )
  )

  private val modelId = NonOpenAIModelId.cerebras_llama_4_scout_17b_16e_instruct

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model = modelId, max_tokens = Some(2000))
      )
      .map(response => println(response.contentHead))
}
