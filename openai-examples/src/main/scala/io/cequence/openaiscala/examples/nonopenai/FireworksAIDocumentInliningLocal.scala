package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import java.nio.file.{Files, Paths}
import java.util.Base64
import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` and `EXAMPLE_PDF_PATH` environment variables to be set
 *
 * Check out the website for more information:
 * https://fireworks.ai/blog/document-inlining-launch
 */
object FireworksAIDocumentInliningLocal extends ExampleBase[OpenAIChatCompletionService] {

  private lazy val localPdfPath = sys.env("EXAMPLE_PDF_PATH")

  private val base64Pdf = {
    val pdfBytes = Files.readAllBytes(Paths.get(localPdfPath))
    Base64.getEncoder.encodeToString(pdfBytes)
  }

  private val fireworksModelPrefix = "accounts/fireworks/models/"
  override val service: OpenAIChatCompletionService = ChatCompletionProvider.fireworks

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent("What are the candidate's BA and MBA GPAs?"),
        ImageURLContent(
          s"data:application/pdf;base64,${base64Pdf}#transform=inline"
        )
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages,
        settings = CreateChatCompletionSettings(
          model =
            fireworksModelPrefix + NonOpenAIModelId.llama_v3p3_70b_instruct, // phi_3_vision_128k_instruct
          temperature = Some(0),
          max_tokens = Some(1000)
        )
      )
      .map(printMessageContent)
}
