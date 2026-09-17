package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.anthropic.domain.OutputFormat
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Anthropic's structured output 400s on `minimum` / `maximum` for numeric properties ("For
 * 'integer' type, properties maximum, minimum are not supported"), so the adapter drops them;
 * `enum` is supported and must survive.
 */
class AnthropicNumericBoundsSpec extends AnyWordSpec with Matchers {

  private val nested: JsonSchema = JsonSchema.Object(
    properties = Seq(
      "stars" -> JsonSchema.Integer(Some("Stars"), minimum = Some(1), maximum = Some(5)),
      "risk" -> JsonSchema.Number(Some("Risk"), `enum` = Seq(0, 0.5, 1)),
      "plain" -> JsonSchema.Integer(Some("Plain")),
      "scores" -> JsonSchema.Array(JsonSchema.Number(maximum = Some(10))),
      "nested" -> JsonSchema.Object(
        properties = Seq("weight" -> JsonSchema.Number(minimum = Some(0)))
      )
    )
  )

  "dropNumericBounds" should {

    "strip minimum / maximum everywhere, naming the paths, and keep everything else" in {
      val (sanitized, dropped) = dropNumericBounds(nested)

      dropped shouldBe Seq("stars", "scores.[]", "nested.weight")

      val properties = sanitized.asInstanceOf[JsonSchema.Object].properties.toMap
      properties("stars") shouldBe JsonSchema.Integer(Some("Stars"))
      // enum survives - Anthropic honours it
      properties("risk") shouldBe JsonSchema.Number(Some("Risk"), `enum` = Seq(0, 0.5, 1))
      properties("plain") shouldBe JsonSchema.Integer(Some("Plain"))
      properties("scores") shouldBe JsonSchema.Array(JsonSchema.Number())
      properties("nested") shouldBe JsonSchema.Object(
        properties = Seq("weight" -> JsonSchema.Number())
      )
    }

    "leave a schema without bounds untouched" in {
      val schema: JsonSchema =
        JsonSchema.Object(properties = Seq("a" -> JsonSchema.String(`enum` = Seq("x"))))
      dropNumericBounds(schema) shouldBe (schema, Nil)
    }
  }

  "toAnthropicSettings" should {

    "send the sanitized schema as the output format" in {
      val settings = CreateChatCompletionSettings(
        model = "claude-haiku-4-5",
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(JsonSchemaDef("rating", strict = false, structure = Left(nested)))
      )

      val outputFormat = toAnthropicSettings(settings).output_format

      val schema = outputFormat.collect { case OutputFormat.JsonSchemaFormat(s) => s }.get
      val properties = schema.asInstanceOf[JsonSchema.Object].properties.toMap
      properties("stars") shouldBe JsonSchema.Integer(Some("Stars"))
      properties("risk") shouldBe JsonSchema.Number(Some("Risk"), `enum` = Seq(0, 0.5, 1))
    }
  }
}
