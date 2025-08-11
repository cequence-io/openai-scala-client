package io.cequence.openaiscala.examples.groq

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.GroqCreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra.OpenAIChatCompletionImplicits
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

/**
 * Requires `GROQ_API_KEY` environment variable to be set.
 */
object GroqCreateChatCompletionJSON extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.groq

  private val jsonSchema: JsonSchema = JsonSchema.Object(
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

  private val messages = Seq(
    SystemMessage("You are a helpful weather assistant that responds in JSON."),
    UserMessage("What is the weather like in Norway? List several cities.")
  )

  private val modelId = NonOpenAIModelId.openai_gpt_oss_120b

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(10000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(
            JsonSchemaDef(
              name = "weather_response",
              strict = true,
              structure = jsonSchema
            )
          )
        )
      )
      .map(json => println(Json.prettyPrint(json)))
}
