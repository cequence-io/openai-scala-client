package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import play.api.libs.json.Json
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set
 *
 * Check out the website for more information:
 * https://fireworks.ai/blog/document-inlining-launch
 */
object FireworksAIDocumentInliningJson extends ExampleBase[OpenAIChatCompletionService] {

  private val fireworksModelPrefix = "accounts/fireworks/models/"
  override val service: OpenAIChatCompletionService = ChatCompletionProvider.fireworks

  val messages: Seq[BaseMessage] = Seq(
    SystemMessage("You are a helpful assistant."),
    UserSeqMessage(
      Seq(
        TextContent(
          "Extract the list of professional associations and accomplishments into JSON"
        ),
        ImageURLContent(
          "https://storage.googleapis.com/fireworks-public/test/sample_resume.pdf#transform=inline"
        )
      )
    )
  )

  private val schema: JsonSchema = JsonSchema.Object(
    properties = Seq(
      "professional_associations" -> JsonSchema.Array(JsonSchema.String()),
      "accomplishment" -> JsonSchema.Array(JsonSchema.String())
    ),
    required = Seq("professional_associations", "accomplishment")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + NonOpenAIModelId.llama_v3p3_70b_instruct,
          temperature = Some(0),
          max_tokens = Some(1000),
//          response_format_type = Some(ChatCompletionResponseFormatType.json_object),
          extra_params = Map(
            "response_format" -> Json.obj(
              "type" -> ChatCompletionResponseFormatType.json_object.toString,
              "schema" -> Json.toJson(schema)
            )
          )
        )
      )
      .map(printMessageContent)
}
