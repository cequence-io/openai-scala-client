package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.{Message, OutputFormat}
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.{JsonSchema, NonOpenAIModelId}
import io.cequence.openaiscala.examples.ExampleBase
import play.api.libs.json.Json

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
object AnthropicCreateMessageWithJsonSchema extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  // note that 'additionalProperties: true' is currently not supported
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

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful weather assistant that responds in JSON."),
    UserMessage("What is the weather like in Norway? List several cities.")
  )

  override protected def run: Future[_] =
    service
      .createMessage(
        messages,
        settings = AnthropicCreateMessageSettings(
          model = NonOpenAIModelId.claude_sonnet_4_5_20250929,
          max_tokens = 16000,
          output_format = Some(
            OutputFormat.JsonSchemaFormat(jsonSchema)
          )
        )
      )
      .map(response => println(Json.prettyPrint(Json.parse(response.text))))
}
