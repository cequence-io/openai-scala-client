package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.examples.fixtures.TestFixtures
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionJSONWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService]
    with TestFixtures {

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are an expert geographer"),
    UserMessage("List all Asian countries in the prescribed JSON format.")
  )

  private val jsonSchema = JsonSchema.Object(
    properties = Seq(
      "countries" -> JsonSchema.Array(
        JsonSchema.Object(
          properties = Seq(
            "country" -> JsonSchema.String(),
            "capital" -> JsonSchema.String(),
            "countrySize" -> JsonSchema.String(
              `enum` = Seq("small", "medium", "large")
            ),
            "commonwealthMember" -> JsonSchema.Boolean(),
            "populationMil" -> JsonSchema.Integer(),
            "ratioOfMenToWomen" -> JsonSchema.Number()
          ),
          required = Seq(
            "country",
            "capital",
            "countrySize",
            "commonwealthMember",
            "populationMil",
            "ratioOfMenToWomen"
          )
        )
      )
    ),
    required = Seq("countries")
  )

  private val modelId = NonOpenAIModelId.gemini_2_0_flash

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(
            JsonSchemaDef(
              name = "countries_response",
              strict = true,
              structure = jsonSchema
            )
          )
        )
      )
      .map(json => println(Json.prettyPrint(json)))
}
