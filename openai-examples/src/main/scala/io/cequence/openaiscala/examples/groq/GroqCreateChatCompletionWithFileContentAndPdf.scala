package io.cequence.openaiscala.examples.groq

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
 * Groq (OpenAI-compatible) probe with a PDF passed as `FileContent`.
 *
 * '''NOTE — this example is expected to fail with HTTP 400.''' Groq's API gateway only accepts
 * content types `text`, `image_url`, and `document`; it does not accept OpenAI's `file`
 * content block. The rejection happens at the gateway (schema validation), so it is
 * model-independent:
 *
 * {{{
 *   'messages.1.content.1.type' is not one of the allowed values
 *   ['text', 'image_url', 'document']
 * }}}
 *
 * The example is kept to (a) document that `FileContent` serializes correctly on the wire
 * (Groq parses it and reports a precise schema error) and (b) flag that supporting PDFs on
 * Groq would require a Groq-specific `document` block translation.
 *
 * Requires `GROQ_API_KEY` and `EXAMPLE_PDF_PATH`.
 */
object GroqCreateChatCompletionWithFileContentAndPdf
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  private val localPdfPath = sys.env("EXAMPLE_PDF_PATH")
  private val pdfBase64 = pdfBase64Source(new File(localPdfPath))

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.groq

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

  private val modelId = NonOpenAIModelId.moonshotai_kimi_k2_instruct_0905

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model = modelId, max_tokens = Some(2000))
      )
      .map(response => println(response.contentHead))
}
