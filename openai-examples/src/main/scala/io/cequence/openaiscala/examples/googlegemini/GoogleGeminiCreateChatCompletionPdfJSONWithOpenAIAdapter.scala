package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future
import io.cequence.openaiscala.examples.BufferedImageHelper
import java.util.Base64
import java.nio.file.Files
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.domain.settings.ChatCompletionResponseFormatType

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionPdfJSONWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService]
    with BufferedImageHelper {

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

  // provide a local jpeg here
  private lazy val localImagePath = sys.env("EXAMPLE_PDF_PATH")

  private val pdfBase64Source =
    Base64.getEncoder.encodeToString(
      Files.readAllBytes(new java.io.File(localImagePath).toPath)
    )

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent("What is in this pdf?"),
        ImageURLContent(s"data:application/pdf;base64,${pdfBase64Source}")
      )
    )
  )

  private val jsonSchema =
    JsonSchema.Array(
      items = JsonSchema.Object(
        properties = Map(
          "itemName" -> JsonSchema.String(),
          "value" -> JsonSchema.String(),
          "comment" -> JsonSchema.String()
        )
      )
    )

  private val modelId = NonOpenAIModelId.gemini_2_5_pro

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(
            JsonSchemaDef(
              name = "extraction_response",
              strict = false,
              structure = jsonSchema
            )
          )
        )
      )
      .map(printMessageContent)
}
