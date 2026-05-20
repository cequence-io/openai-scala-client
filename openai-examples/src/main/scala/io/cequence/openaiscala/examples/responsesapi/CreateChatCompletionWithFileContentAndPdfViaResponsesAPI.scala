package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{BufferedImageHelper, Example}
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService

import java.io.File
import scala.concurrent.Future

/**
 * End-to-end check that `OpenAIResponsesChatCompletionService` (Responses API → Chat
 * Completion adapter) correctly forwards `FileContent` as `InputMessageContent.File` and that
 * the response is mapped back through the adapter.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `EXAMPLE_PDF_PATH`.
 */
object CreateChatCompletionWithFileContentAndPdfViaResponsesAPI
    extends Example
    with BufferedImageHelper {

  private val chatService = OpenAIResponsesChatCompletionService(service)

  private val localPath = sys.env("EXAMPLE_PDF_PATH")
  private val localFile = new File(localPath)

  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant that summarizes documents."),
    UserSeqMessage(
      Seq(
        TextContent("Summarize this document in 3 bullet points."),
        FileContent(
          fileData = Some(fileBase64DataUrl(localFile)),
          filename = Some(localFile.getName)
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
