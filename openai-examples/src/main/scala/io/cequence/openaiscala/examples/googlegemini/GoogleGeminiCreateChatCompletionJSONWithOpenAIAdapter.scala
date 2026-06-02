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
 *
 * '''Gemini `response_schema` size limit (3.x)''': Gemini 3.x flash models reject requests
 * whose serialized `response_schema` exceeds roughly 40 KB of JSON with a generic `400
 * INVALID_ARGUMENT` ("Request contains an invalid argument.") — no field-level detail. The
 * cutoff was measured empirically at ~44 KB (first/last 35-element slices of a large entity
 * batch failed; 32–34-element slices passed). The limit is on the schema bytes, independent of
 * `strict` mode or which JSON Schema constructs are used.
 *
 * If you generate the schema dynamically (e.g. one entity per property) and approach this
 * threshold, batch your work so each request's schema stays under ~40 KB. For an entity-
 * extraction workload sized like ours: `batch_size = 40` -> ~44 KB schema -> 400; dropping to
 * `batch_size = 30` -> ~37 KB schema -> succeeds.
 */
object GoogleGeminiCreateChatCompletionJSONWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService]
    with TestFixtures {

  private val timeout = 5 * 60 * 1000 // 5 minutes

  private val modelId = NonOpenAIModelId.gemini_3_1_flash_lite_preview

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
          ),
          // Behavioral probe: if this Object description is honored, 'capital' is ALL CAPS.
          description =
            Some("A single country record. The 'capital' value MUST be in ALL UPPERCASE.")
        ),
        // Behavioral probe: if this Array description is honored, we get exactly 2 items.
        description =
          Some("The list MUST contain EXACTLY 2 country entries, no more and no less.")
      )
    ),
    required = Seq("countries"),
    description = Some("Top-level container for the countries response")
  )

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
