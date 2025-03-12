package io.cequence.openaiscala.examples.grok

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
 * Requires `GROK_API_KEY` environment variable to be set.
 */
object GrokCreateChatCompletionJSON extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.grok

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
    SystemMessage("You are a helpful weather assistant that responds in JSON"),
    UserMessage("What is the weather like in Norway per major cities/towns?")
  )

  private val modelId = NonOpenAIModelId.grok_2

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
