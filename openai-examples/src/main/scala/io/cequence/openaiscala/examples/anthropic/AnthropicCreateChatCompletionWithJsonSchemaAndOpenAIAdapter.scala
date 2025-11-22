package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra.OpenAIChatCompletionImplicits
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionWithJsonSchemaAndOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    ChatCompletionProvider.anthropic()

  // Define the JSON schema for weather responses
  // Note: Anthropic will automatically set 'additionalProperties: false' on all objects where it's not specified
  private val weatherSchema: JsonSchema = JsonSchema.Object(
    properties = Seq(
      "response" -> JsonSchema.Array(
        items = JsonSchema.Object(
          properties = Seq(
            "city" -> JsonSchema.String(),
            "temperature" -> JsonSchema.String(),
            "weather" -> JsonSchema.String()
          ),
          required = Seq("city", "temperature", "weather")
        )
      )
    ),
    required = Seq("response")
  )

  private val weatherSchemaDef = JsonSchemaDef(
    name = "weather_response",
    strict =
      true, // Note: strict mode is not supported by Anthropic but will be ignored gracefully
    structure = Left(weatherSchema)
  )

  private val messages = Seq(
    SystemMessage("You are a helpful weather assistant that responds in JSON."),
    UserMessage("What is the weather like in Norway? List several cities.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_4_5_20250929,
          max_tokens = Some(16000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(weatherSchemaDef)
        )
      )
      .map { json =>
        println(Json.prettyPrint(json))
      }
}
