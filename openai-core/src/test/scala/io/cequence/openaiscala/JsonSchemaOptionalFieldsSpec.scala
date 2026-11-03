package io.cequence.openaiscala

import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.JsonSchema.{AnyOf, Array, Nullable, Object, String}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** [[JsonSchema.optionalFields]]: required nullable properties become optional ones. */
class JsonSchemaOptionalFieldsSpec extends AnyWordSpec with Matchers {

  private val text = String(Some("d"))

  "optionalFields" should {

    "turn a required nullable property into an optional one of its inner schema" in {
      val schema = Object(
        properties = Seq("a" -> Nullable(text), "b" -> String()),
        required = Seq("a", "b")
      )

      JsonSchema.optionalFields(schema) shouldBe Object(
        properties = Seq("a" -> text, "b" -> String()),
        required = Seq("b")
      )
    }

    "convert nested objects at every depth" in {
      val inner = Object(
        properties = Seq("x" -> Nullable(String()), "y" -> String()),
        required = Seq("x", "y"),
        description = Some("inner")
      )
      val schema = Object(
        properties = Seq("o" -> Nullable(inner)),
        required = Seq("o")
      )

      JsonSchema.optionalFields(schema) shouldBe Object(
        properties = Seq(
          "o" -> Object(
            properties = Seq("x" -> String(), "y" -> String()),
            required = Seq("y"),
            description = Some("inner")
          )
        ),
        required = Nil
      )
    }

    "convert objects inside array items" in {
      val item = Object(Seq("x" -> Nullable(String())), Seq("x"))
      val schema = Array(item, Some("list"))

      JsonSchema.optionalFields(schema) shouldBe
        Array(Object(Seq("x" -> String()), Nil), Some("list"))
    }

    "convert objects inside a non-nullable union branch" in {
      val branch = Object(Seq("x" -> Nullable(String())), Seq("x"))
      val schema = AnyOf(Seq(branch, String()), Some("u"))

      JsonSchema.optionalFields(schema) shouldBe
        AnyOf(Seq(Object(Seq("x" -> String()), Nil), String()), Some("u"))
    }

    "keep a nullable property that is not required" in {
      val schema = Object(Seq("a" -> Nullable(text)), Nil)

      JsonSchema.optionalFields(schema) shouldBe schema
    }

    "return a schema without nullables equal and keep additionalProperties" in {
      val schema = Object(
        properties = Seq("a" -> String(), "n" -> Array(String())),
        required = Seq("a"),
        additionalProperties = Some(false),
        description = Some("top")
      )

      JsonSchema.optionalFields(schema) shouldBe schema
    }

    "keep additionalProperties and the description of a converted object" in {
      val schema = Object(
        properties = Seq("a" -> Nullable(text)),
        required = Seq("a"),
        additionalProperties = Some(false),
        description = Some("top")
      )

      JsonSchema.optionalFields(schema) shouldBe Object(
        properties = Seq("a" -> text),
        required = Nil,
        additionalProperties = Some(false),
        description = Some("top")
      )
    }
  }
}
