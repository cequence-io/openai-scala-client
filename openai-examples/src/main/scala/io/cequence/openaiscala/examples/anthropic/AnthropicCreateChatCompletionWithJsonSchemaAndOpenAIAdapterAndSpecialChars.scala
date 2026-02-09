package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
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
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra.OpenAIChatCompletionImplicits
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateChatCompletionWithJsonSchemaAndOpenAIAdapterAndSpecialChars
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    AnthropicServiceFactory.asOpenAI(
      timeouts = Some(
        Timeouts(
          requestTimeout = Some(200000),
          readTimeout = Some(200000)
        )
      ),
      withCache = true
    )

  // Extraction schema with Unicode property - note that this was failing at some point but seems to be fixed now (Jan 2026)
  val contractSchema: JsonSchema = JsonSchema.Object(
    properties = Seq(
      "Název smlouvy" -> JsonSchema.String(description = Some("Název smlouvy"))
    ),
    required = Seq("Název smlouvy")
  )

  private val jsonSchemaDef = JsonSchemaDef(
    name = "contract_response",
    strict =
      true, // Note: strict mode is not supported by Anthropic but will be ignored gracefully
    structure = Left(contractSchema)
  )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant that responds in JSON."),
    UserMessage("Just give me a random json with these properties in Czech.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSON[JsObject](
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_4_5_20250929,
          max_tokens = Some(16000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(jsonSchemaDef)
        ),
        // no retries
        maxRetries = None
      )
      .map { json =>
        println(Json.prettyPrint(json))
      }
}
