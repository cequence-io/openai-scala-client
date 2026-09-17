package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.domain.JsonSchema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * `minimum` / `maximum` / `enum` on the typed `Integer` / `Number` (used by the TypeSafe
 * adapter; mostly unsupported by LLM structured output) - written only when set, read back.
 */
class JsonSchemaNumericConstraintsSpec extends AnyWordSpec with Matchers {

  "Integer / Number schema" should {

    "write nothing extra when the constraints are unset" in {
      Json.toJson[JsonSchema](JsonSchema.Integer(Some("Age"))) shouldBe
        Json.obj("type" -> "integer", "description" -> "Age")
      Json.toJson[JsonSchema](JsonSchema.Number()) shouldBe Json.obj("type" -> "number")
    }

    "write and read the constraints" in {
      val integer: JsonSchema =
        JsonSchema.Integer(Some("Stars"), minimum = Some(1), maximum = Some(5))
      val number: JsonSchema = JsonSchema.Number(`enum` = Seq(0, 0.5, 1))

      val integerJson = Json.toJson(integer)
      integerJson shouldBe Json.obj(
        "type" -> "integer",
        "description" -> "Stars",
        "minimum" -> 1,
        "maximum" -> 5
      )
      integerJson.as[JsonSchema] shouldBe integer

      val numberJson = Json.toJson(number)
      numberJson shouldBe Json.obj("type" -> "number", "enum" -> Seq(0.0, 0.5, 1.0))
      numberJson.as[JsonSchema] shouldBe number
    }

    "read a schema without them (as before)" in {
      Json.parse("""{"type":"integer","description":"n"}""").as[JsonSchema] shouldBe
        JsonSchema.Integer(Some("n"))
    }
  }
}
