package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService

import scala.concurrent.Future

/**
 * Sends a structured-output request through `OpenAIResponsesChatCompletionService` to verify
 * the strict-schema path: `schemaDef.strict = true` triggers
 * `JsonSchema.setAdditionalPropertiesToFalseByDefault`, which recursively closes nested
 * `JsonSchema.Object`s. The nested `address` object below confirms the recursion fires past
 * the top-level object.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object CreateChatCompletionWithJsonSchemaViaResponsesAPI extends Example {

  private val chatService = OpenAIResponsesChatCompletionService(service)

  private val personSchema = JsonSchemaDef(
    name = "person",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "name" -> JsonSchema.String(description = Some("Full name")),
          "age" -> JsonSchema.Integer(description = Some("Age in years")),
          "address" -> JsonSchema.Object(
            properties = Seq(
              "street" -> JsonSchema.String(),
              "city" -> JsonSchema.String(),
              "country" -> JsonSchema.String()
            ),
            required = Seq("street", "city", "country")
          ),
          "hobbies" -> JsonSchema.Array(
            items = JsonSchema.String(),
            description = Some("List of hobbies")
          )
        ),
        required = Seq("name", "age", "address", "hobbies")
      )
    )
  )

  override protected def run: Future[_] =
    chatService
      .createChatCompletion(
        messages = Seq(
          SystemMessage("You return structured data about fictional people."),
          UserMessage(
            "Make up a profile for a fictional Italian software engineer living in Berlin."
          )
        ),
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_5_mini,
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(personSchema)
        )
      )
      .map { response =>
        println(response.contentHead)
        response.usage.foreach(u => println(s"\n[usage] $u"))
      }
}
