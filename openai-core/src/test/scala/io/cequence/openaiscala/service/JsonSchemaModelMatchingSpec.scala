package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  BaseMessage,
  ImageURLContent,
  JsonSchema,
  SystemMessage,
  TextContent,
  UserMessage,
  UserSeqMessage
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * Which model ids get native json_schema mode in `createChatCompletionWithJSON`: exact, the
 * `-<id>` suffix, and the Bedrock cross-region spelling of OpenAI models - but never a Bedrock
 * Anthropic id by its bare Claude name.
 */
class JsonSchemaModelMatchingSpec extends AnyWordSpec with Matchers {

  private val schema = JsonSchemaDef(
    "c",
    strict = true,
    JsonSchema.Object(properties = Seq("a" -> JsonSchema.String()), required = Seq("a"))
  )

  private def schemaMode(
    model: String,
    listed: Seq[String]
  ): Boolean = {
    val (_, settings) = OpenAIChatCompletionExtra.handleOutputJsonSchema(
      Seq(UserMessage("x")),
      CreateChatCompletionSettings(
        model,
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(schema)
      ),
      "test",
      jsonSchemaModels = listed
    )
    settings.response_format_type.contains(ChatCompletionResponseFormatType.json_schema)
  }

  "handleOutputJsonSchema" should {

    "match exact ids and the -<id> suffix" in {
      schemaMode("gpt-5.6-luna", Seq("gpt-5.6-luna")) shouldBe true
      schemaMode("accounts/fireworks/models/x-gpt-oss", Seq("gpt-oss")) shouldBe true
      schemaMode("gpt-5.6-luna", Seq("gpt-5.6-sol")) shouldBe false
    }

    "match the Bedrock cross-region spelling of an OpenAI model by its bare id" in {
      schemaMode("us.openai.gpt-5.6-luna", Seq("gpt-5.6-luna")) shouldBe true
      schemaMode("global.openai.gpt-6-sol", Seq("gpt-6-sol")) shouldBe true
      // json_schema live-verified on Bedrock's global.openai.gpt-6.1-sol (2026-09-29)
      schemaMode("global.openai.gpt-6.1-sol", Seq("gpt-6.1-sol")) shouldBe true
      schemaMode("openai.gpt-6-luna", Seq("gpt-6-luna")) shouldBe true
    }

    "not match a Bedrock Anthropic id by its bare Claude name (Bedrock rejects it there)" in {
      schemaMode("eu.anthropic.claude-opus-5", Seq("claude-opus-5")) shouldBe false
      schemaMode("anthropic.claude-haiku-4-5", Seq("claude-haiku-4-5")) shouldBe false
    }
  }

  "jsonSchemaFromPrompt" should {

    // the JSON-object fallback of a model not listed as json_schema-capable
    def fallback(messages: BaseMessage*) =
      OpenAIChatCompletionExtra
        .handleOutputJsonSchema(
          messages,
          CreateChatCompletionSettings("unlisted", jsonSchema = Some(schema)),
          "test",
          jsonSchemaModels = Seq("other")
        )
        ._1

    val schemaJson =
      Json.toJson(schema.structure)(io.cequence.openaiscala.JsonFormats.eitherJsonSchemaWrites)

    "read back the schema the fallback appended to the last user message, and drop it" in {
      val messages =
        Seq(SystemMessage("Be brief."), UserMessage("First"), UserMessage("A ticket"))
      val withSchema = fallback(messages: _*)

      withSchema should not be messages
      OpenAIChatCompletionExtra.jsonSchemaFromPrompt(withSchema) shouldBe Some(
        messages -> schemaJson
      )
    }

    "keep an empty or blank user message the schema was appended to" in {
      Seq("", "  \n").foreach { blank =>
        val messages = Seq(SystemMessage("Judge it."), UserMessage(blank))
        OpenAIChatCompletionExtra.jsonSchemaFromPrompt(fallback(messages: _*)) shouldBe Some(
          messages -> schemaJson
        )
      }
    }

    "drop the message the fallback added for the schema alone" in {
      val withSchema = fallback(SystemMessage("Be brief."))

      withSchema should have size 2
      OpenAIChatCompletionExtra.jsonSchemaFromPrompt(withSchema) shouldBe Some(
        Seq(SystemMessage("Be brief.")) -> schemaJson
      )
    }

    "drop the part the fallback appended to a message with images" in {
      val message = UserSeqMessage(
        Seq(TextContent("What is it?"), ImageURLContent("data:image/png;base64,AAAA"))
      )

      OpenAIChatCompletionExtra.jsonSchemaFromPrompt(fallback(message)) shouldBe Some(
        Seq(message) -> schemaJson
      )
    }

    "find nothing in a prompt without the appendix, or with text after it" in {
      OpenAIChatCompletionExtra.jsonSchemaFromPrompt(
        Seq(UserMessage("A ticket"))
      ) shouldBe None
      OpenAIChatCompletionExtra.jsonSchemaFromPrompt(Nil) shouldBe None

      val withSchema =
        fallback(UserMessage("A ticket")).collect { case UserMessage(content, _) => content }
      OpenAIChatCompletionExtra.jsonSchemaFromPrompt(
        Seq(UserMessage(withSchema.last + " and more"))
      ) shouldBe None
    }
  }
}
