package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{JsonSchema, UserMessage}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
      schemaMode("openai.gpt-6-luna", Seq("gpt-6-luna")) shouldBe true
    }

    "not match a Bedrock Anthropic id by its bare Claude name (Bedrock rejects it there)" in {
      schemaMode("eu.anthropic.claude-opus-5", Seq("claude-opus-5")) shouldBe false
      schemaMode("anthropic.claude-haiku-4-5", Seq("claude-haiku-4-5")) shouldBe false
    }
  }
}
