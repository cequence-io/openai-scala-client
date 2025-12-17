package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.examples.{ExampleBase, TestFixtures}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionJSONWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService]
    with TestFixtures {

  private val timeout = 5 * 60 * 1000 // 5 minutes

  override val service: OpenAIChatCompletionService =
    GeminiServiceFactory.asOpenAI(
      timeouts = Some(
        Timeouts(
          requestTimeout = Some(timeout),
          readTimeout = Some(timeout)
        )
      )
    )

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

  private val modelId = NonOpenAIModelId.gemini_3_flash_preview

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          reasoning_effort = Some(ReasoningEffort.low),
          jsonSchema = Some(
            JsonSchemaDef(
              name = "countries_response",
              strict = true,
              structure = jsonSchema
            )
          ),
          max_tokens = Some(10000)
        )
      )
      .map(json => println(Json.prettyPrint(json)))
}
