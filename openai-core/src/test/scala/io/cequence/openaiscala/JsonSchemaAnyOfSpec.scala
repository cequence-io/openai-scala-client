package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, Json}

/**
 * [[JsonSchema.AnyOf]] - JSON Schema's `anyOf`, and its nullable form
 * ([[JsonSchema.Nullable]], how an `Option` field derives): written as `anyOf`, read from
 * `anyOf` as written and from a `type` array.
 */
class JsonSchemaAnyOfSpec extends AnyWordSpec with Matchers {

  private val unit: JsonSchema =
    JsonSchema.Nullable(JsonSchema.String(Some("The unit, if known"), Seq("F", "C")))

  private val unitJson = Json.obj(
    "anyOf" -> Json.arr(
      Json.obj("type" -> "string", "enum" -> Seq("F", "C")),
      Json.obj("type" -> "null")
    ),
    "description" -> "The unit, if known"
  )

  private val circle = JsonSchema.Object(Seq("radius" -> JsonSchema.Number()), Seq("radius"))
  private val square = JsonSchema.Object(Seq("side" -> JsonSchema.Number()), Seq("side"))

  "Nullable" should {

    "build a union with null, the schema's description on the union" in {
      unit shouldBe JsonSchema.AnyOf(
        Seq(JsonSchema.String(None, Seq("F", "C")), JsonSchema.Null()),
        Some("The unit, if known")
      )
      Json.toJson(unit) shouldBe unitJson
    }

    "match a union of a schema and null, either order, with the union's description" in {
      unit match {
        case JsonSchema.Nullable(inner) =>
          inner shouldBe JsonSchema.String(Some("The unit, if known"), Seq("F", "C"))
        case other => fail(s"not nullable: $other")
      }

      JsonSchema.AnyOf(
        Seq(JsonSchema.Null(), JsonSchema.Boolean(Some("own"))),
        Some("union")
      ) match {
        case JsonSchema.Nullable(inner) => inner shouldBe JsonSchema.Boolean(Some("own"))
        case other                      => fail(s"not nullable: $other")
      }

      JsonSchema.AnyOf(Seq(circle, square)) match {
        case JsonSchema.Nullable(_) => fail("a union of two objects is not nullable")
        case _                      => succeed
      }
    }

    "never nest, and add null to a union" in {
      JsonSchema.Nullable(unit) shouldBe unit
      JsonSchema.Nullable(JsonSchema.Null()) shouldBe JsonSchema.Null()
      JsonSchema.Nullable(JsonSchema.AnyOf(Seq(circle, square), Some("A shape"))) shouldBe
        JsonSchema.AnyOf(Seq(circle, square, JsonSchema.Null()), Some("A shape"))
    }
  }

  "AnyOf" should {

    "be written and read back as written" in {
      val shape = JsonSchema.AnyOf(Seq(circle, square), Some("A shape"))
      val json = Json.toJson[JsonSchema](shape)

      json shouldBe Json.obj(
        "anyOf" -> Json.arr(Json.toJson[JsonSchema](circle), Json.toJson[JsonSchema](square)),
        "description" -> "A shape"
      )
      json.as[JsonSchema] shouldBe shape
      unitJson.as[JsonSchema] shouldBe unit
      // a branch's own description stays where it is
      Json
        .obj(
          "anyOf" -> Json.arr(
            Json.obj("type" -> "null"),
            Json.obj("type" -> "boolean", "description" -> "own")
          ),
          "description" -> "union"
        )
        .as[JsonSchema] shouldBe JsonSchema.AnyOf(
        Seq(JsonSchema.Null(), JsonSchema.Boolean(Some("own"))),
        Some("union")
      )
    }

    "be read from a type array - one branch per type, an enum's values by type" in {
      Json
        .obj(
          "type" -> Json.arr("string", "null"),
          "enum" -> Json.arr("F", "C", JsNull),
          "description" -> "The unit, if known"
        )
        .as[JsonSchema] shouldBe unit

      Json
        .obj("type" -> Json.arr("string", "integer"), "description" -> "An id")
        .as[JsonSchema] shouldBe JsonSchema.AnyOf(
        Seq(JsonSchema.String(), JsonSchema.Integer()),
        Some("An id")
      )

      // a single type in an array is that type
      Json
        .obj("type" -> Json.arr("boolean"), "description" -> "A flag")
        .as[JsonSchema] shouldBe
        JsonSchema.Boolean(Some("A flag"))
    }

    "refuse a branch that does not read, and oneOf, as before" in {
      Json
        .obj("anyOf" -> Json.arr(Json.obj("type" -> "string"), Json.obj("format" -> "date")))
        .validate[JsonSchema]
        .isError shouldBe true
      Json
        .obj("oneOf" -> Json.arr(Json.obj("type" -> "string"), Json.obj("type" -> "integer")))
        .validate[JsonSchema]
        .isError shouldBe true
    }

    "carry its description" in {
      JsonSchema.descriptionOf(unit) shouldBe Some("The unit, if known")
      JsonSchema.withDescription(unit, Some("Other")) shouldBe
        JsonSchema.AnyOf(
          Seq(JsonSchema.String(None, Seq("F", "C")), JsonSchema.Null()),
          Some("Other")
        )
    }

    "be closed inside by setAdditionalPropertiesToFalse" in {
      JsonSchema.setAdditionalPropertiesToFalse(
        JsonSchema.AnyOf(Seq(circle, JsonSchema.Null()))
      ) shouldBe JsonSchema.AnyOf(
        Seq(circle.copy(additionalProperties = Some(false)), JsonSchema.Null())
      )
    }
  }

  "toStrictSchema (the chat completions body's strict schema)" should {

    "close an object inside anyOf and a nullable object - OpenAI 400s an open one" in {
      val strict = OpenAIChatCompletionExtra.toStrictSchema(
        Left(
          JsonSchema.Object(
            Seq("location" -> JsonSchema.Nullable(circle)),
            Seq("location")
          )
        )
      )

      def field(
        map: Any,
        name: String
      ) = map.asInstanceOf[Map[String, Any]](name)

      field(strict, "additionalProperties") shouldBe false
      val branches = field(field(field(strict, "properties"), "location"), "anyOf")
      field(
        branches.asInstanceOf[scala.collection.Seq[Any]].head,
        "additionalProperties"
      ) shouldBe
        false

      // a map-form schema with a type array
      val mapForm = OpenAIChatCompletionExtra.toStrictSchema(
        Right(Map("type" -> Seq("object", "null"), "properties" -> Map.empty[String, Any]))
      )
      mapForm("additionalProperties") shouldBe false
    }
  }

  "an object schema" should {

    "be read with its properties in their order" in {
      val names = Seq("f", "e", "d", "c", "b", "a")
      val schema = JsonSchema.Object(names.map(_ -> JsonSchema.String()), names, Some(false))

      Json.toJson[JsonSchema](schema).as[JsonSchema] shouldBe schema
    }
  }
}
