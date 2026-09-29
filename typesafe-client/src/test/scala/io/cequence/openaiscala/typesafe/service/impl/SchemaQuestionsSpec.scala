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

    "build levels for bounds anywhere on the number line" in {
      val plan = SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "big":{"type":"integer","minimum":3000000000,"maximum":3000000002},
            |  "neg":{"type":"integer","minimum":-2,"maximum":0}
            |}}""".stripMargin
        )
      )
      plan.questions("big") shouldBe
        ScoreQuestion("Big", "3000000000", "3000000001", "3000000002")
      plan.root.fields.collect { case ("big", s: ScoreSlot) => s.levels } shouldBe
        Seq(Seq(BigDecimal(3000000000L), BigDecimal(3000000001L), BigDecimal(3000000002L)))
      plan.questions("neg") shouldBe ScoreQuestion("Neg", "-2", "-1", "0")
    }

    "refuse a string enum with non-string values instead of trimming it" in {
      val e = the[IllegalArgumentException] thrownBy SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "mixed":{"type":"string","enum":["a",42,"b"]},
            |  "tags":{"type":"array","items":{"type":"string","enum":["x",null]}}
            |}}""".stripMargin
        )
      )
      e.getMessage should include("mixed: a string enum with non-string values")
      e.getMessage should include("tags: a string enum with non-string values")
    }

    "fold duplicate enum values so the count, the questions and the output agree" in {
      val plan = SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "pick":{"type":"string","enum":["a","a","b"]},
            |  "tags":{"type":"array","items":{"type":"string","enum":["a","a","b"]}}
            |}}""".stripMargin
        )
      )
      plan.questions("pick") shouldBe ChoiceQuestion.ofLabels("Pick", "a", "b")
      plan.questions.keySet should contain allOf ("tags.[a]", "tags.[b]")
      plan.questions.size shouldBe 3

      val json = SchemaQuestions.assemble(
        plan,
        Map(
          "pick" -> ChoiceAnswer("a", 1, Map("a" -> 1.0, "b" -> 0.0)),
          "tags.[a]" -> NoulAnswer(0.9),
          "tags.[b]" -> NoulAnswer(0.1)
        ),
        0.5
      )
      (json \ "tags").as[Seq[String]] shouldBe Seq("a")

      // 256 raw values that fold to 255 are within the cap
      val wide = (1 to 255).map(i => s"o$i") :+ "o1"
      noException should be thrownBy SchemaQuestions.plan(
        Json.obj(
          "type" -> "object",
          "properties" -> Json.obj("pick" -> Json.obj("type" -> "string", "enum" -> wide))
        )
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

  "confidence fields" should {

    // both spellings, nested objects, every slot kind, and an unmatched confidence-like name
    val schema = Json.parse(
      """{"type":"object","properties":{
        |  "is_urgent":{"type":"boolean"},
        |  "is_urgent_confidence":{"type":"number"},
        |  "department":{"type":"string","enum":["billing","technical"]},
        |  "departmentConfidence":{"type":"number"},
        |  "department_confidence":{"type":["number","null"]},
        |  "stars":{"type":"integer","minimum":1,"maximum":3},
        |  "starsConfidence":{"type":"number"},
        |  "topics":{"type":"array","items":{"type":"string","enum":["payments","pricing"]}},
        |  "topics_confidence":{"type":"number"},
        |  "customer":{"type":"object","properties":{
        |    "isAngry":{"type":"boolean"},
        |    "isAngryConfidence":{"type":"number"},
        |    "tier":{"type":"string","enum":["free","pro"]}
        |  }},
        |  "customer_confidence":{"type":"number"}
        |}}""".stripMargin
    )

    val score = ScoreAnswer(
      score = 1.1,
      confidence = 0.61234,
      legend = Map(0 -> JsNumber(1), 1 -> JsNumber(2), 2 -> JsNumber(3)),
      probabilities = Map(0 -> 0.1, 1 -> 0.7, 2 -> 0.2)
    )

    val answers: Map[String, Answer] = Map(
      "is_urgent" -> NoulAnswer(0.3),
      "department" -> ChoiceAnswer(
        "billing",
        0.83335,
        Map("billing" -> 0.9, "technical" -> 0.1)
      ),
      "stars" -> score,
      "topics.[payments]" -> NoulAnswer(0.95),
      "topics.[pricing]" -> NoulAnswer(0.2),
      "customer.isAngry" -> NoulAnswer(0.9),
      "customer.tier" -> ChoiceAnswer("pro", 0.4, Map("free" -> 0.3, "pro" -> 0.7))
    )

    "be dropped from the questions - at any depth, both spellings" in {
      SchemaQuestions.plan(schema).questions.keySet shouldBe Set(
        "is_urgent",
        "department",
        "stars",
        "topics.[payments]",
        "topics.[pricing]",
        "customer.isAngry",
        "customer.tier"
      )
    }

    "be filled from the answers, right after their base field" in {
      val json = SchemaQuestions.assemble(SchemaQuestions.plan(schema), answers, 0.5)

      json.keys.toSeq shouldBe Seq(
        "is_urgent",
        "is_urgent_confidence",
        "department",
        "departmentConfidence",
        "department_confidence",
        "stars",
        "starsConfidence",
        "topics",
        "topics_confidence",
        "customer",
        "customer_confidence"
      )

      // boolean: the probability of the emitted answer (false here, so 1 - 0.3)
      (json \ "is_urgent").as[Boolean] shouldBe false
      (json \ "is_urgent_confidence").as[BigDecimal] shouldBe BigDecimal("0.7")
      // choice / score: their peakedness confidence, rounded half-up to 4 decimals
      (json \ "departmentConfidence").as[BigDecimal] shouldBe BigDecimal("0.8334")
      (json \ "department_confidence").as[BigDecimal] shouldBe BigDecimal("0.8334")
      (json \ "starsConfidence").as[BigDecimal] shouldBe BigDecimal("0.6123")
      // multi-select: the weakest option decision (pricing: 1 - 0.2)
      (json \ "topics_confidence").as[BigDecimal] shouldBe BigDecimal("0.8")
      // object: the minimum over everything underneath (tier: 0.4)
      (json \ "customer_confidence").as[BigDecimal] shouldBe BigDecimal("0.4")
      (json \ "customer").as[JsObject].keys.toSeq shouldBe Seq(
        "isAngry",
        "isAngryConfidence",
        "tier"
      )
      (json \ "customer" \ "isAngryConfidence").as[BigDecimal] shouldBe BigDecimal("0.9")
    }

    "follow the noul threshold for booleans" in {
      val plan = SchemaQuestions.plan(schema)
      // threshold 0.3: noul 0.3 reads as true, so its confidence is the noul itself
      val json = SchemaQuestions.assemble(plan, answers, noulThreshold = 0.3)
      (json \ "is_urgent").as[Boolean] shouldBe true
      (json \ "is_urgent_confidence").as[BigDecimal] shouldBe BigDecimal("0.3")
    }

    "work on a legacy map-form schema too" in {
      val mapSchema: Map[String, Any] = Map(
        "type" -> "object",
        "properties" -> Map(
          "ok" -> Map("type" -> "boolean"),
          "okConfidence" -> Map("type" -> "number")
        )
      )
      val plan = SchemaQuestions.plan(
        Json.toJson[Either[JsonSchema, Map[String, Any]]](Right(mapSchema))
      )
      plan.questions.keySet shouldBe Set("ok")
      SchemaQuestions.assemble(plan, Map("ok" -> NoulAnswer(0.91)), 0.5) shouldBe
        Json.obj("ok" -> true, "okConfidence" -> 0.91)
    }

    "leave a confidence-like field without a sibling (or of a confidence field) to the planner" in {
      val e = the[IllegalArgumentException] thrownBy SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "a":{"type":"boolean"},
            |  "a_confidence":{"type":"number"},
            |  "a_confidence_confidence":{"type":"number"},
            |  "orphan_confidence":{"type":"number"}
            |}}""".stripMargin
        )
      )
      e.getMessage should include("orphan_confidence: a number without an enum")
      e.getMessage should include("a_confidence_confidence: a number without an enum")
      e.getMessage should not include ("a_confidence:")
    }

    "refuse a confidence field that is not a number" in {
      val e = the[IllegalArgumentException] thrownBy SchemaQuestions.plan(
        Json.parse(
          """{"type":"object","properties":{
            |  "a":{"type":"boolean"},
            |  "aConfidence":{"type":"string"}
            |}}""".stripMargin
        )
      )
      e.getMessage should include("aConfidence: a confidence field (of 'a') must be a number")
    }

    "report the questions without a usable answer instead of a confidence" in {
      val plan = SchemaQuestions.plan(schema)
      val customer = plan.root.fields.toMap.apply("customer")

      SchemaQuestions.confidence(customer, answers - "customer.tier", 0.5) shouldBe
        Left(Seq("customer.tier"))
      SchemaQuestions.confidence(
        customer,
        answers + ("customer.tier" -> UnknownAnswer("bounding_box", Json.obj())),
        0.5
      ) shouldBe Left(Seq("customer.tier"))
      SchemaQuestions.confidence(customer, answers, 0.5) shouldBe Right(BigDecimal("0.4000"))
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
