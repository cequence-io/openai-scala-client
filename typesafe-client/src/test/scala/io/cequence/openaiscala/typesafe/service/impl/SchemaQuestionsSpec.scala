package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaWrites
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.typesafe.domain._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class SchemaQuestionsSpec extends AnyWordSpec with Matchers {

  import SchemaQuestions._

  private val typedSchema: JsonSchema = JsonSchema.Object(
    properties = Seq(
      "department" -> JsonSchema.String(
        description = Some("Which team should handle this"),
        `enum` = Seq("billing", "technical", "sales")
      ),
      "is_urgent" -> JsonSchema.Boolean(),
      "topics" -> JsonSchema.Array(
        JsonSchema.String(`enum` = Seq("payments", "integration", "pricing")),
        description = Some("What the message is about")
      ),
      "customer" -> JsonSchema.Object(
        properties = Seq("isAngry" -> JsonSchema.Boolean(Some("The customer is angry")))
      )
    )
  )

  private def typedJson: JsValue =
    Json.toJson[Either[JsonSchema, Map[String, Any]]](Left(typedSchema))

  "plan" should {

    "turn a typed schema into questions named by their paths" in {
      val plan = SchemaQuestions.plan(typedJson)

      plan.questions.keySet shouldBe Set(
        "department",
        "is_urgent",
        "topics.[payments]",
        "topics.[integration]",
        "topics.[pricing]",
        "customer.isAngry"
      )

      plan.questions("department") shouldBe ChoiceQuestion.ofLabels(
        "Which team should handle this",
        "billing",
        "technical",
        "sales"
      )
      // no description -> humanised property name
      plan.questions("is_urgent") shouldBe NoulQuestion("Is urgent")
      plan.questions("customer.isAngry") shouldBe NoulQuestion("The customer is angry")
      plan.questions("topics.[payments]") shouldBe
        NoulQuestion("Does 'payments' apply? (What the message is about)")

      plan.root.fields.map(_._1) shouldBe Seq("department", "is_urgent", "topics", "customer")
    }

    "map numeric enums and small ranges onto score levels (raw-map schemas)" in {
      val plan = SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "stars":{"type":"integer","minimum":1,"maximum":5,"description":"Star rating"},
            |  "weight":{"type":"number","enum":[0.5, 0.25, 1]},
            |  "nullable":{"type":["boolean","null"]}
            |}}""".stripMargin
        )
      )

      // levels go out as text (the API refuses numbers) in ascending order
      plan.questions("stars") shouldBe ScoreQuestion("Star rating", "1", "2", "3", "4", "5")
      plan.questions("weight") shouldBe ScoreQuestion("Weight", "0.25", "0.5", "1")
      plan.questions("nullable") shouldBe NoulQuestion("Nullable")
    }

    "take the typed Integer / Number constraints too" in {
      val plan = SchemaQuestions.plan(
        Json.toJson[Either[JsonSchema, Map[String, Any]]](
          Left(
            JsonSchema.Object(
              Seq(
                "stars" -> JsonSchema
                  .Integer(Some("Stars"), minimum = Some(1), maximum = Some(3)),
                "risk" -> JsonSchema.Number(`enum` = Seq(0, 0.5, 1))
              )
            )
          )
        )
      )
      plan.questions("stars") shouldBe ScoreQuestion("Stars", "1", "2", "3")
      plan.questions("risk") shouldBe ScoreQuestion("Risk", "0", "0.5", "1")
      plan.root.fields.collect { case (n, s: ScoreSlot) => n -> s.levels } shouldBe Seq(
        "stars" -> Seq(1, 2, 3).map(BigDecimal(_)),
        "risk" -> Seq(BigDecimal(0), BigDecimal("0.5"), BigDecimal(1))
      )
    }

    "refuse what System One cannot answer, listing every offending path" in {
      val e = the[IllegalArgumentException] thrownBy SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "summary":{"type":"string"},
            |  "count":{"type":"integer"},
            |  "wide":{"type":"integer","minimum":0,"maximum":1000},
            |  "items":{"type":"array","items":{"type":"object","properties":{"a":{"type":"boolean"}}}},
            |  "nested":{"type":"object","properties":{"ref":{"$ref":"#/x"},"ok":{"type":"boolean"}}},
            |  "empty":{"type":"object","properties":{}}
            |}}""".stripMargin
        )
      )

      e.getMessage should include("summary: a free-form string")
      e.getMessage should include("count: a number without an enum")
      e.getMessage should include("wide: a minimum..maximum range wider than 32")
      e.getMessage should include("items: an array whose items are not a string enum")
      e.getMessage should include("nested.ref: anyOf / oneOf / allOf / $ref")
      e.getMessage should include("empty: an object without properties")
      e.getMessage should not include "nested.ok"
    }

    "refuse a string enum wider than a choice allows, naming the path" in {
      val wide = Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(
          "target" -> Json.obj("type" -> "string", "enum" -> (1 to 256).map(i => s"el_$i"))
        )
      )
      val e = the[IllegalArgumentException] thrownBy SchemaQuestions.plan(wide)
      e.getMessage should include(
        "target: an enum with 256 values - a choice allows at most 255"
      )
    }

    "refuse a non-object root" in {
      an[IllegalArgumentException] should be thrownBy
        SchemaQuestions.plan(Json.parse("""{"type":"boolean"}"""))
    }
  }

  "assemble" should {

    "fold the answers into a document of the schema" in {
      val plan = SchemaQuestions.plan(typedJson)
      val answers: Map[String, Answer] = Map(
        "department" -> ChoiceAnswer(
          "technical",
          0.6,
          Map("billing" -> 0.2, "technical" -> 0.7, "sales" -> 0.1)
        ),
        "is_urgent" -> NoulAnswer(0.55),
        "topics.[payments]" -> NoulAnswer(0.9),
        "topics.[integration]" -> NoulAnswer(0.5),
        "topics.[pricing]" -> NoulAnswer(0.1),
        "customer.isAngry" -> NoulAnswer(0.3)
      )

      SchemaQuestions.assemble(plan, answers, noulThreshold = 0.5) shouldBe Json.obj(
        "department" -> "technical",
        "is_urgent" -> true,
        "topics" -> Json.arr("payments", "integration"),
        "customer" -> Json.obj("isAngry" -> false)
      )

      // a stricter threshold flips the borderline nouls
      SchemaQuestions.assemble(plan, answers, noulThreshold = 0.8) shouldBe Json.obj(
        "department" -> "technical",
        "is_urgent" -> false,
        "topics" -> Json.arr("payments"),
        "customer" -> Json.obj("isAngry" -> false)
      )
    }

    "give an integer its most likely level and a number the expected value" in {
      val plan = SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "stars":{"type":"integer","minimum":1,"maximum":3},
            |  "risk":{"type":"number","enum":[0, 0.5, 1]}
            |}}""".stripMargin
        )
      )
      val score = ScoreAnswer(
        score = 1.3,
        confidence = 0.7,
        legend = Map(0 -> JsNumber(1), 1 -> JsNumber(2), 2 -> JsNumber(3)),
        probabilities = Map(0 -> 0.2, 1 -> 0.7, 2 -> 0.1)
      )

      val json = SchemaQuestions.assemble(plan, Map("stars" -> score, "risk" -> score), 0.5)

      (json \ "stars").as[Int] shouldBe 2
      // 0.2 * 0 + 0.7 * 0.5 + 0.1 * 1
      (json \ "risk").as[BigDecimal] shouldBe BigDecimal("0.45")
    }

    "complain about a missing or mistyped answer" in {
      val plan = SchemaQuestions.plan(typedJson)
      val e = the[IllegalStateException] thrownBy
        SchemaQuestions.assemble(plan, Map("department" -> NoulAnswer(1)), 0.5)
      e.getMessage should include("department")
    }
  }

  "humanize" should {
    "split snake, kebab and camel case" in {
      humanize("is_urgent") shouldBe "Is urgent"
      humanize("isUrgent") shouldBe "Is urgent"
      humanize("is-urgent") shouldBe "Is urgent"
      humanize("HTTPStatus2xx") shouldBe "Httpstatus2xx"
      humanize("x") shouldBe "X"
    }
  }

}
