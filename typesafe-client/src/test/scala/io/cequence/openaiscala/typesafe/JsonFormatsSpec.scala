package io.cequence.openaiscala.typesafe

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import scala.collection.immutable.ListMap

/**
 * The wire format, pinned against what the official SDKs send and accept: the question
 * encodings are the expectations of `typesafe-sdk-python`'s `test_questions.py`, the response
 * fixtures are the docs' quick-start bodies and the malformed-answer table of its
 * `test_responses.py`.
 */
class JsonFormatsSpec extends AnyWordSpec with Matchers {

  private val quickStartRequest = Json.parse(
    """{
      |  "state": "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. I'm losing sales. Please help ASAP.",
      |  "model": "jev-latest",
      |  "questions": {
      |    "department": {
      |      "type": "choice",
      |      "instructions": "Which team should handle this",
      |      "criteria": {
      |        "billing": "Payment or subscription issues",
      |        "technical": "Bugs or integration problems",
      |        "sales": "Pricing or account questions"
      |      }
      |    },
      |    "frustration": {
      |      "type": "score",
      |      "instructions": "How frustrated the customer appears",
      |      "criteria": [
      |        "Calm, just stating facts",
      |        "Frustrated but civil",
      |        "Very angry, strong language"
      |      ]
      |    },
      |    "is_urgent": {
      |      "type": "noul",
      |      "instructions": "The message conveys urgency or time-sensitivity"
      |    }
      |  }
      |}""".stripMargin
  )

  private val quickStartResponse = Json.parse(
    """{
      |  "model": "jev-latest",
      |  "answers": {
      |    "department": {
      |      "type": "choice",
      |      "choice": "technical",
      |      "probabilities": { "billing": 0.159, "technical": 0.84, "sales": 0.001 },
      |      "confidence": 0.596
      |    },
      |    "frustration": {
      |      "type": "score",
      |      "score": 1.035,
      |      "legend": {
      |        "0": "Calm, just stating facts",
      |        "1": "Frustrated but civil",
      |        "2": "Very angry, strong language"
      |      },
      |      "probabilities": { "0": 0.1, "1": 0.765, "2": 0.135 },
      |      "confidence": 0.842
      |    },
      |    "is_urgent": { "type": "noul", "noul": 0.999 }
      |  },
      |  "usage": { "input_tokens": 312, "output_tokens": 48 }
      |}""".stripMargin
  )

  "Question writes" should {

    "encode the three kinds exactly like the official Python SDK" in {
      Json.toJson[Question](NoulQuestion("Spam?")) shouldBe
        Json.obj("type" -> "noul", "instructions" -> "Spam?")

      Json.toJson[Question](ChoiceQuestion.ofLabels("Tone?", "calm")) shouldBe
        Json.obj(
          "type" -> "choice",
          "instructions" -> "Tone?",
          "criteria" -> Json.obj("calm" -> JsNull)
        )

      Json.toJson[Question](ScoreQuestion("Quality?", "bad", "good")) shouldBe
        Json.obj(
          "type" -> "score",
          "instructions" -> "Quality?",
          "criteria" -> Seq("bad", "good")
        )
    }

    "omit what was not set, as the SDK does (omit_defaults)" in {
      Json.toJson[Question](NoulQuestion(Some(JsString("Spam?")))) shouldBe
        Json.obj("type" -> "noul", "instructions" -> "Spam?")

      Json.toJson[Question](ChoiceQuestion(ListMap("a" -> None))) shouldBe
        Json.obj("type" -> "choice", "criteria" -> Json.obj("a" -> JsNull))

      Json.toJson[Question](ScoreQuestion(Seq(JsString("good")))) shouldBe
        Json.obj("type" -> "score", "criteria" -> Seq("good"))

      Json.toJson[Question](NoulQuestion(Some(JsString("")), Some(NoulCriteria()))) shouldBe
        Json.obj("type" -> "noul", "instructions" -> "", "criteria" -> Json.obj())
    }

    "write the noul criteria under the API's `true` / `false` keys" in {
      Json.toJson[Question](
        NoulQuestion("Spam?", yes = "Unsolicited ads", no = "A real conversation")
      ) shouldBe
        Json.obj(
          "type" -> "noul",
          "instructions" -> "Spam?",
          "criteria" -> Json.obj("true" -> "Unsolicited ads", "false" -> "A real conversation")
        )
    }

    "accept structured (JSON) instructions and descriptions" in {
      val question = NoulQuestion(
        instructions = Some(Json.obj("task" -> "Identify unsolicited advertising.")),
        criteria = Some(NoulCriteria(yes = Some(Json.arr("ads", "promotions")), no = None))
      )

      Json.toJson[Question](question) shouldBe Json.obj(
        "type" -> "noul",
        "instructions" -> Json.obj("task" -> "Identify unsolicited advertising."),
        "criteria" -> Json.obj("true" -> Json.arr("ads", "promotions"))
      )
    }

    "keep the declared option order" in {
      val json = Json.toJson[Question](ChoiceQuestion("q", "z" -> "1", "a" -> "2", "m" -> "3"))

      (json \ "criteria").as[JsObject].keys.toSeq shouldBe Seq("z", "a", "m")
    }

    "refuse a score question without levels and a choice without options" in {
      an[IllegalArgumentException] should be thrownBy ScoreQuestion(Nil)
      an[IllegalArgumentException] should be thrownBy ChoiceQuestion(
        ListMap.empty[String, Option[JsValue]]
      )
    }

    // the API answers 400 "Too many choices. Must have at most 255 choices."
    "refuse a choice question with more than 255 options" in {
      noException should be thrownBy
        ChoiceQuestion.ofLabels("Which?", (1 to 255).map(i => s"o$i"): _*)
      val e = the[IllegalArgumentException] thrownBy
        ChoiceQuestion.ofLabels("Which?", (1 to 256).map(i => s"o$i"): _*)
      e.getMessage should include("at most 255 options (got 256)")
    }

    // the API answers 422 "Input should be a valid string / dictionary / list" per level
    "refuse a numeric score level" in {
      an[IllegalArgumentException] should be thrownBy
        ScoreQuestion(Seq(JsNumber(1), JsNumber(2)))
      noException should be thrownBy
        ScoreQuestion(Seq(JsString("1"), Json.obj("label" -> "low")))
    }

    // the API answers 400 "Noul question must have criteria or instructions: <name>"
    "refuse a noul question with nothing to evaluate" in {
      an[IllegalArgumentException] should be thrownBy NoulQuestion()
      an[IllegalArgumentException] should be thrownBy NoulQuestion(criteria =
        Some(NoulCriteria())
      )
      noException should be thrownBy NoulQuestion(criteria =
        Some(NoulCriteria(yes = Some(JsString("spam"))))
      )
    }
  }

  "SystemOneRequest writes" should {

    "produce the docs' quick-start request body" in {
      val request = SystemOneRequest(
        state = JsString(
          "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. I'm losing sales. Please help ASAP."
        ),
        model = TypeSafeModelId.jev_latest,
        questions = Map(
          "department" -> ChoiceQuestion(
            "Which team should handle this",
            "billing" -> "Payment or subscription issues",
            "technical" -> "Bugs or integration problems",
            "sales" -> "Pricing or account questions"
          ),
          "frustration" -> ScoreQuestion(
            "How frustrated the customer appears",
            "Calm, just stating facts",
            "Frustrated but civil",
            "Very angry, strong language"
          ),
          "is_urgent" -> NoulQuestion("The message conveys urgency or time-sensitivity")
        )
      )

      Json.toJson(request) shouldBe quickStartRequest
    }

    "round-trip through the reads" in {
      val request = quickStartRequest.as[SystemOneRequest]

      request.model shouldBe "jev-latest"
      request.questions.keySet shouldBe Set("department", "frustration", "is_urgent")
      request.questions("department") shouldBe a[ChoiceQuestion]
      Json.toJson(request) shouldBe quickStartRequest
    }

    "refuse a request without questions" in {
      an[IllegalArgumentException] should be thrownBy
        SystemOneRequest(JsString("x"), "jev-latest", Map.empty)
    }

    // a number / boolean / null state is a 422 on the API's side (str | dict | list)
    "refuse a state that is neither text nor a JSON object or array" in {
      val q = Map("q" -> (NoulQuestion("x"): Question))
      an[IllegalArgumentException] should be thrownBy SystemOneRequest(
        JsNumber(42),
        "jev-latest",
        q
      )
      an[IllegalArgumentException] should be thrownBy SystemOneRequest(JsNull, "jev-latest", q)
      an[IllegalArgumentException] should be thrownBy SystemOneRequest(
        JsBoolean(true),
        "jev-latest",
        q
      )
      noException should be thrownBy SystemOneRequest(Json.arr("a", "b"), "jev-latest", q)
      noException should be thrownBy SystemOneRequest(Json.obj("a" -> 1), "jev-latest", q)
    }
  }

  "SystemOneResponse reads" should {

    "decode the docs' quick-start response" in {
      val response = quickStartResponse.as[SystemOneResponse]

      response.model shouldBe "jev-latest"
      response.usage shouldBe Usage(Some(312), Some(48))
      response.requestId shouldBe None

      val department = response.choice("department")
      department.choice shouldBe "technical"
      department.confidence shouldBe 0.596
      department.probabilities shouldBe Map(
        "billing" -> 0.159,
        "technical" -> 0.84,
        "sales" -> 0.001
      )
      department.ranked.map(_._1) shouldBe Seq("technical", "billing", "sales")

      val frustration = response.score("frustration")
      frustration.score shouldBe 1.035
      frustration.legend shouldBe Map(
        0 -> JsString("Calm, just stating facts"),
        1 -> JsString("Frustrated but civil"),
        2 -> JsString("Very angry, strong language")
      )
      frustration.probabilities shouldBe Map(0 -> 0.1, 1 -> 0.765, 2 -> 0.135)
      frustration.levels shouldBe Seq(0, 1, 2)
      frustration.mostLikelyLevel shouldBe 1
      frustration.describe(1) shouldBe Some("Frustrated but civil")
      frustration.normalized shouldBe (1.035 / 2)

      response.noul("is_urgent").noul shouldBe 0.999
      response.noul("is_urgent").isYes() shouldBe true

      response.nouls.keySet shouldBe Set("is_urgent")
      response.choices.keySet shouldBe Set("department")
      response.scores.keySet shouldBe Set("frustration")
      response.unknown shouldBe empty
    }

    "explain a wrong or missing name" in {
      val response = quickStartResponse.as[SystemOneResponse]

      the[NoSuchElementException] thrownBy response.noul("department") should have message
        "The answer 'department' is a choice answer, not a noul one."

      (the[NoSuchElementException] thrownBy response.score(
        "nope"
      )).getMessage should startWith(
        "No answer named 'nope'; the answers are: "
      )
    }

    "tolerate fields it does not know" in {
      val json = Json.obj(
        "model" -> "test",
        "usage" -> Json.obj(
          "input_tokens" -> 1,
          "output_tokens" -> 1,
          "reasoning_tokens" -> 9,
          "billing_units" -> 1
        ),
        "answers" -> Json.obj(
          "spam" -> Json.obj("type" -> "noul", "noul" -> 0.9, "explanation" -> "spammy")
        )
      )

      val response = json.as[SystemOneResponse]
      response.noul("spam") shouldBe NoulAnswer(0.9)
      response.usage shouldBe Usage(Some(1), Some(1))
    }

    "keep an answer of a type this version does not model, instead of dropping it" in {
      val mystery = Json.obj("type" -> "aurora", "value" -> 3)
      val json = Json.obj(
        "model" -> "test",
        "usage" -> Json.obj("input_tokens" -> 1, "output_tokens" -> 1),
        "answers" -> Json
          .obj("spam" -> Json.obj("type" -> "noul", "noul" -> 0.9), "mystery" -> mystery)
      )

      val response = json.as[SystemOneResponse]
      response.answers("mystery") shouldBe UnknownAnswer("aurora", mystery)
      response.unknown.keySet shouldBe Set("mystery")
      response.nouls.keySet shouldBe Set("spam")
    }

    "accept usage without token counts, as the SDK does" in {
      val json = Json.obj("model" -> "test", "usage" -> Json.obj(), "answers" -> Json.obj())

      json.as[SystemOneResponse].usage shouldBe Usage(None, None)
    }

    "reject malformed answers, pointing at the field (the SDK's table)" in {
      def bodyWith(answers: JsObject): JsValue =
        Json.obj("model" -> "test", "usage" -> Json.obj(), "answers" -> answers)

      val cases: Seq[(JsValue, String)] = Seq(
        Json.obj("usage" -> Json.obj(), "answers" -> Json.obj()) -> "/model",
        bodyWith(Json.obj("n" -> Json.obj("type" -> "noul"))) -> "/answers/n/noul",
        bodyWith(
          Json.obj(
            "c" -> Json.obj("type" -> "choice", "choice" -> "a", "probabilities" -> Json.obj())
          )
        ) -> "/answers/c/confidence",
        bodyWith(
          Json.obj(
            "c" -> Json
              .obj("type" -> "choice", "confidence" -> 0.5, "probabilities" -> Json.obj())
          )
        ) -> "/answers/c/choice",
        bodyWith(
          Json.obj(
            "s" -> Json.obj(
              "type" -> "score",
              "score" -> 1.0,
              "confidence" -> 1.0,
              "legend" -> Json.arr(),
              "probabilities" -> Json.obj()
            )
          )
        ) -> "/answers/s/legend",
        bodyWith(
          Json.obj(
            "s" -> Json.obj(
              "type" -> "score",
              "score" -> 1.0,
              "confidence" -> 1.0,
              "legend" -> Json.obj("x" -> "bad"),
              "probabilities" -> Json.obj()
            )
          )
        ) -> "/answers/s/legend/x",
        bodyWith(Json.obj("c" -> "not-a-mapping")) -> "/answers/c/type"
      )

      cases.foreach { case (json, expectedPath) =>
        val result = json.validate[SystemOneResponse]
        withClue(s"$json should fail at $expectedPath: ") {
          result.isError shouldBe true
          result.asEither.left.get.map(_._1.toString) should contain(expectedPath)
        }
      }
    }
  }

  "Answer format" should {

    "round-trip every kind" in {
      val answers: Seq[Answer] = Seq(
        NoulAnswer(0.98),
        ChoiceAnswer("angry", 0.9, Map("angry" -> 0.8, "calm" -> 0.1, "excited" -> 0.1)),
        ScoreAnswer(
          1.7,
          0.9,
          Map(
            0 -> JsString("Can wait"),
            1 -> Json.obj("level" -> "soon"),
            2 -> Json.arr("now")
          ),
          Map(0 -> 0.1, 1 -> 0.1, 2 -> 0.8)
        ),
        UnknownAnswer("aurora", Json.obj("type" -> "aurora", "value" -> 3))
      )

      answers.foreach { answer =>
        Json.toJson(answer).as[Answer] shouldBe answer
      }
    }

    "carry the discriminator the API uses" in {
      (Json.toJson[Answer](NoulAnswer(0.5)) \ "type").as[String] shouldBe "noul"
      (Json.toJson[Answer](ChoiceAnswer("a", 1, Map("a" -> 1))) \ "type")
        .as[String] shouldBe "choice"
      (Json.toJson[Answer](ScoreAnswer(0, 1, Map(0 -> JsString("x")), Map(0 -> 1))) \ "type")
        .as[String] shouldBe "score"
    }
  }

  "ModelMetadata reads" should {

    "decode the models listing and fail on a missing field" in {
      val ok = Json.obj(
        "name" -> "jev-latest",
        "description" -> "General-purpose system one model.",
        "release_date" -> "2026-09-15"
      )
      ok.as[ModelMetadata] shouldBe ModelMetadata(
        "jev-latest",
        "General-purpose system one model.",
        "2026-09-15"
      )

      (ok - "release_date").validate[ModelMetadata].isError shouldBe true
    }
  }
}
