package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.settings.GroqCreateChatCompletionSettingsOps._
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import play.api.libs.json.{JsObject, Json}
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra.OpenAIChatCompletionImplicits

import scala.concurrent.Future

/**
 * Requires `GROQ_API_KEY` environment variable to be set.
 */
object GroqCreateChatCompletionJSONWithDeepseekR1
    extends ExampleBase[OpenAIChatCompletionService] {

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
    SystemMessage(
      s"""You are a helpful weather assistant that responds in JSON.
        |Here is the schema:
        |${Json.prettyPrint(Json.toJson(jsonSchema))}""".stripMargin
    ),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.deepseek_r1_distill_llama_70b

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(
            JsonSchemaDef(
              name = "weather_response",
              strict = true,
              structure = jsonSchema
            )
          )
        ).setMaxCompletionTokens(4000)
      )
      .map(json => println(Json.prettyPrint(json)))
}
