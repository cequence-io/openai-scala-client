package io.cequence.openaiscala.vertexai.service.impl

import com.google.cloud.vertexai.api.Type
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ToVertexAIGenerationConfigSpec extends AnyWordSpec with Matchers {

  "toVertexAI (GenerationConfig)" should {

    "send a nullable field as its schema with the nullable flag, a union as anyOf" in {
      val circle = JsonSchema.Object(Seq("radius" -> JsonSchema.Number()), Seq("radius"))
      val square = JsonSchema.Object(Seq("side" -> JsonSchema.Number()), Seq("side"))
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(
          JsonSchemaDef(
            "shapes",
            strict = true,
            Left(
              JsonSchema.Object(
                Seq(
                  "nickname" -> JsonSchema.Nullable(JsonSchema.String(Some("A nickname"))),
                  "shape" -> JsonSchema.AnyOf(
                    Seq(circle, square, JsonSchema.Null()),
                    Some("The shape")
                  )
                ),
                Seq("nickname", "shape")
              )
            )
          )
        )
      )

      val schema = toVertexAI(settings).getResponseSchema

      val nickname = schema.getPropertiesMap.get("nickname")
      nickname.getType shouldBe Type.STRING
      nickname.getNullable shouldBe true
      nickname.getDescription shouldBe "A nickname"
      nickname.getAnyOfCount shouldBe 0

      val shape = schema.getPropertiesMap.get("shape")
      shape.getType shouldBe Type.TYPE_UNSPECIFIED
      shape.getNullable shouldBe true
      shape.getDescription shouldBe "The shape"
      shape.getAnyOfCount shouldBe 2
      shape.getAnyOf(0).getPropertiesMap.containsKey("radius") shouldBe true
      shape.getAnyOf(1).getPropertiesMap.containsKey("side") shouldBe true
    }

    "wire logprobs/top_logprobs to responseLogprobs/logprobs and never touch topK" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        logprobs = Some(true),
        top_logprobs = Some(5)
      )

      val config = toVertexAI(settings)

      config.hasResponseLogprobs shouldBe true
      config.getResponseLogprobs shouldBe true
      config.hasLogprobs shouldBe true
      config.getLogprobs shouldBe 5
      config.hasTopK shouldBe false
    }

    "not set responseLogprobs/logprobs when logprobs is None" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        logprobs = None,
        top_logprobs = Some(5)
      )

      val config = toVertexAI(settings)

      config.hasResponseLogprobs shouldBe false
      config.hasLogprobs shouldBe false
      config.hasTopK shouldBe false
    }

    "not set logprobs (count) when logprobs is explicitly false" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        logprobs = Some(false),
        top_logprobs = Some(5)
      )

      val config = toVertexAI(settings)

      config.hasResponseLogprobs shouldBe true
      config.getResponseLogprobs shouldBe false
      config.hasLogprobs shouldBe false
    }

    "wire seed to GenerationConfig.seed" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        seed = Some(42)
      )

      val config = toVertexAI(settings)

      config.getSeed shouldBe 42
    }
  }
}
